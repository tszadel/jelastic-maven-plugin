package com.jelastic;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Uploads the artifact to a Jelastic based cloud and registers it in the deployment manager, without deploying it.
 */
@Mojo(name = "publish", threadSafe = true)
public class PublishMojo extends JelasticMojo {

    public void execute() throws MojoExecutionException {
        run(false);
    }
}
