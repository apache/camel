package org.apache.camel.maven;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.debezium.connector.mysql.MySqlConnector;
import io.debezium.connector.mysql.MySqlConnectorConfig;
import io.debezium.relational.history.FileDatabaseHistory;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "generate-mysql-connector-config", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateMySqlConnectorConfig extends AbstractGenerateConnectorConfig {

    @Override
    protected Set<String> getRequiredFields() {
        return new HashSet<>(Arrays.asList(MySqlConnectorConfig.PASSWORD.name(), MySqlConnectorConfig.SERVER_NAME.name()));
    }

    @Override
    protected Map<String, Object> getOverrideFields() {
        final Map<String, Object> overrideFields = new HashMap<>();
        overrideFields.put(MySqlConnectorConfig.DATABASE_HISTORY.name(), FileDatabaseHistory.class);
        overrideFields.put(MySqlConnectorConfig.TOMBSTONES_ON_DELETE.name(), false);

        return overrideFields;
    }

    @Override
    protected SourceConnector getConnector() {
        return new MySqlConnector();
    }

    @Override
    protected Class<?> getConnectorConfigClass() {
        return MySqlConnectorConfig.class;
    }
}
