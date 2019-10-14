package org.apache.camel.maven;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.debezium.connector.postgresql.PostgresConnector;
import io.debezium.connector.postgresql.PostgresConnectorConfig;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "generate-postgres-connector-config", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GeneratePostgresConnectorConfig extends AbstractGenerateConnectorConfig {

    @Override
    protected Set<String> getRequiredFields() {
        return new HashSet<>(Arrays.asList(PostgresConnectorConfig.PASSWORD.name(), PostgresConnectorConfig.SERVER_NAME.name()));
    }

    @Override
    protected Map<String, Object> getOverrideFields() {
        final Map<String, Object> overrideFields = new HashMap<>();
        overrideFields.put(PostgresConnectorConfig.TOMBSTONES_ON_DELETE.name(), false);

        return overrideFields;
    }

    @Override
    protected SourceConnector getConnector() {
        return new PostgresConnector();
    }

    @Override
    protected Class<?> getConnectorConfigClass() {
        return PostgresConnectorConfig.class;
    }
}
