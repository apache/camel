package org.apache.camel.maven;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.camel.maven.config.ConnectorConfigGenerator;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractGenerateConnectorConfig extends AbstractMojo {
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/connector-configurations")
    protected File generatedSrcDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Set<String> requiredFields = getRequiredFields();
        final Map<String, Object> overrideFields = getOverrideFields();

        final ConnectorConfigGenerator connectorConfigGenerator = ConnectorConfigGenerator.create(getConnector(), getConnectorConfigClass(), requiredFields, overrideFields);
        try {
            final File parentPath = new File(generatedSrcDir, connectorConfigGenerator.getPackageName().replace(".", "/"));
            final File connectorConfigClassFile = new File(parentPath, connectorConfigGenerator.getClassName() + ".java");
            if(!connectorConfigClassFile.exists()){
                connectorConfigClassFile.getParentFile().mkdirs();
                connectorConfigClassFile.createNewFile();
            }
            connectorConfigGenerator.printGeneratedClass(new FileOutputStream(connectorConfigClassFile));
        } catch (IOException e) {
            throw new MojoFailureException("error", e);
        }
    }

    protected abstract Set<String> getRequiredFields();

    protected abstract Map<String, Object> getOverrideFields();

    protected abstract SourceConnector getConnector();

    protected abstract Class<?> getConnectorConfigClass();
}
