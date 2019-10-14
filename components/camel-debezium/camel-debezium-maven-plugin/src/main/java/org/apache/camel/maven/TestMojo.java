package org.apache.camel.maven;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.debezium.connector.mysql.MySqlConnector;
import io.debezium.connector.mysql.MySqlConnectorConfig;
import io.debezium.relational.history.FileDatabaseHistory;
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
        final Set<String> requiredFields = new HashSet<>(Arrays.asList(MySqlConnectorConfig.PASSWORD.name(), MySqlConnectorConfig.SERVER_NAME.name()));
        final Map<String, Object> overrideFields = new HashMap<>();
        overrideFields.put(MySqlConnectorConfig.DATABASE_HISTORY.name(), FileDatabaseHistory.class);
        overrideFields.put(MySqlConnectorConfig.TOMBSTONES_ON_DELETE.name(), false);

        final ConnectorConfigGenerator connectorConfigGenerator = ConnectorConfigGenerator.create(new MySqlConnector(), MySqlConnectorConfig.class, requiredFields, overrideFields);
        try {
            final File parentPath = new File(generatedSrcDir, connectorConfigGenerator.getPackageName().replace(".", "/"));
            final File connectorConfigClassFile = new File(parentPath, connectorConfigGenerator.getClassName() + ".java");
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
