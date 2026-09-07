package com.jelastic;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Which jar goes online.
 *
 * <p>{@code target/} is a scratch directory, not a manifest: a {@code copy-dependencies} execution, a shaded test jar
 * or a leftover from a previous {@code finalName} all sit next to the application. Picking the biggest file among
 * them deploys the wrong code and says nothing — the log only ever printed the winner. Selection starts from what
 * Maven produced; size only breaks a tie between siblings of the same build.</p>
 */
public class ChooseArtifactTest {

    @Rule
    public TemporaryFolder target = new TemporaryFolder();

    /**
     * The case that motivated the change: something bigger than the application, that the build never produced.
     * {@code maven-dependency-plugin} writing into {@code target/} is enough to create it.
     */
    @Test
    public void aBiggerStrangerDoesNotWin() throws Exception {
        File application = artifact("ts-edl-0.0.1.jar", 300);
        File stranger = artifact("spring-boot-3.4.0.jar", 900);

        File chosen = JelasticMojo.chooseArtifact(target.getRoot(), Arrays.asList(stranger, application),
                Collections.singletonList(application), null);

        assertEquals(application, chosen);
    }

    /**
     * A Spring Boot project with {@code <classifier>exec</classifier>} produces two legitimate jars, and only the
     * executable one runs. It is the bigger of the two — which is why size stays the tie-break.
     */
    @Test
    public void betweenSiblingsTheExecutableOneWins() throws Exception {
        File plain = artifact("ts-edl-0.0.1.jar", 300);
        File executable = artifact("ts-edl-0.0.1-exec.jar", 900);

        File chosen = JelasticMojo.chooseArtifact(target.getRoot(), Arrays.asList(plain, executable),
                Arrays.asList(plain, executable), null);

        assertEquals(executable, chosen);
    }

    /**
     * {@code mvn jelastic:deploy} in an invocation of its own: Maven has attached no file to the project, so there is
     * nothing to prefer. The old behaviour is the only one left, and it must not become an error.
     */
    @Test
    public void withoutBuildOutputsTheBiggestStillWins() throws Exception {
        File small = artifact("ts-edl-0.0.1.jar", 300);
        File big = artifact("ts-edl-0.0.1-exec.jar", 900);

        File chosen = JelasticMojo.chooseArtifact(target.getRoot(), Arrays.asList(small, big),
                Collections.<File>emptyList(), null);

        assertEquals(big, chosen);
    }

    @Test
    public void aNamedArtifactIsTakenWhateverItsSize() throws Exception {
        File named = artifact("ts-edl-0.0.1.jar", 300);
        File bigger = artifact("ts-edl-0.0.1-exec.jar", 900);

        File chosen = JelasticMojo.chooseArtifact(target.getRoot(), Arrays.asList(named, bigger),
                Arrays.asList(named, bigger), "ts-edl-0.0.1.jar");

        assertEquals(named, chosen);
    }

    /**
     * It used to warn and deploy the biggest file instead. Someone who names an artifact has a reason to, and a typo
     * in that name then put an unintended jar online — the warning scrolling past in a green CI log.
     */
    @Test
    public void aNamedArtifactThatIsMissingIsAnError() throws Exception {
        File other = artifact("ts-edl-0.0.1-exec.jar", 900);

        try {
            JelasticMojo.chooseArtifact(target.getRoot(), Collections.singletonList(other),
                    Collections.singletonList(other), "ts-edl-0.0.2.jar");
            fail("a missing named artifact must stop the deployment");
        } catch (MojoExecutionException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("ts-edl-0.0.2.jar"));
        }
    }

    /**
     * The named artifact may be one the listing filter did not keep — a {@code .zip}, or a path written by hand. It
     * exists on disk, so it is deployed: naming it is the explicit gesture.
     */
    @Test
    public void aNamedArtifactOutsideTheListingIsStillTaken() throws Exception {
        File listed = artifact("ts-edl-0.0.1.jar", 300);
        File aside = artifact("ts-edl-0.0.1.zip", 100);

        File chosen = JelasticMojo.chooseArtifact(target.getRoot(), Collections.singletonList(listed),
                Collections.singletonList(listed), "ts-edl-0.0.1.zip");

        assertEquals(aside, chosen);
    }

    /** Two candidates of the same size: the answer must not depend on the order the file system returned them. */
    @Test
    public void theChoiceDoesNotDependOnTheListingOrder() throws Exception {
        File application = artifact("ts-edl-0.0.1.jar", 300);
        File stranger = artifact("spring-boot-3.4.0.jar", 300);
        List<File> outputs = Collections.singletonList(application);

        assertEquals(application, JelasticMojo.chooseArtifact(target.getRoot(),
                Arrays.asList(application, stranger), outputs, null));
        assertEquals(application, JelasticMojo.chooseArtifact(target.getRoot(),
                Arrays.asList(stranger, application), outputs, null));
    }

    private File artifact(String name, int bytes) throws Exception {
        File file = new File(target.getRoot(), name);
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(new byte[bytes]);
        } finally {
            out.close();
        }
        return file;
    }
}
