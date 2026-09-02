package com.jelastic;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Uploads the artifact to a Jelastic based cloud and deploys it to the target environment.
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.INSTALL, threadSafe = true)
public class DeployMojo extends JelasticMojo {

    public void execute() throws MojoExecutionException {
        run(true);
    }
}
