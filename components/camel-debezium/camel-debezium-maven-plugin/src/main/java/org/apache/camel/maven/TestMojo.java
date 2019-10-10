package org.apache.camel.maven;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import io.debezium.connector.postgresql.PostgresConnector;
import io.debezium.connector.postgresql.PostgresConnectorConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "hello")
public class TestMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/connector-configurations")
    protected File generatedSrcDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final ConnectorConfigGenerator connectorConfigGenerator = ConnectorConfigGenerator.create(new PostgresConnector(), PostgresConnectorConfig.class);
        try {
            final File connectorConfigClassFile = new File(generatedSrcDir, connectorConfigGenerator.getClassName() + ".java");
            if(!connectorConfigClassFile.exists()){
                connectorConfigClassFile.getParentFile().mkdirs();
                connectorConfigClassFile.createNewFile();
            }
            connectorConfigGenerator.printGeneratedClass(new FileOutputStream(connectorConfigClassFile));
        } catch (IOException e) {
            getLog().info(e.getMessage());
            throw new MojoFailureException("error", e);
        }

        getLog().info("Hello folks!");
    }
}
