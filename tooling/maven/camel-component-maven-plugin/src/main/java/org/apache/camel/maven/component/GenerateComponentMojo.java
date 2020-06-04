package org.apache.camel.maven.component;

import org.apache.camel.maven.packaging.AbstractGenerateMojo;
import org.apache.camel.maven.packaging.EndpointSchemaGeneratorMojo;
import org.apache.camel.maven.packaging.GenerateConfigurerMojo;
import org.apache.camel.maven.packaging.PackageJandexMojo;
import org.apache.camel.maven.packaging.PrepareComponentMojo;
import org.apache.camel.maven.packaging.SpiGeneratorMojo;
import org.apache.camel.maven.packaging.TypeConverterLoaderGeneratorMojo;
import org.apache.camel.maven.packaging.ValidateComponentMojo;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * To be used by 3rd party Camel component developers to generate metadata.
 */
@Mojo(name = "generate", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class GenerateComponentMojo extends AbstractGenerateMojo {

    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject currentProject;
    @Component
    protected MavenProjectHelper currentProjectHelper;
    @Component
    protected BuildContext currentBuildContext;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.project = currentProject;
        this.projectHelper = currentProjectHelper;
        this.buildContext = currentBuildContext;

        super.execute();
    }

    @Override
    protected void doExecute() throws MojoFailureException, MojoExecutionException {
        // do not sync pom file for this goal as we are standalone
        project.setContextValue("syncPomFile", "false");

       // jandex
        invoke(PackageJandexMojo.class);
        // generate-type-converter-loader
        invoke(TypeConverterLoaderGeneratorMojo.class);
        // generate-spi
        invoke(SpiGeneratorMojo.class);
        // generate-configurer
        invoke(GenerateConfigurerMojo.class);
        // generate-endpoint-schema
        invoke(EndpointSchemaGeneratorMojo.class);
        // prepare-components
        invoke(PrepareComponentMojo.class);
        // validate-components
        invoke(ValidateComponentMojo.class);
    }
}
