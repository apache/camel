package org.apache.camel.component.debezium;

import org.apache.camel.component.debezium.configuration.MySqlConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Represents a Debezium endpoint which is used for interacting with Debezium
 * embedded engine.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "debezium-mysql", title = "Debezium MySQL Connector", syntax = "debezium-mysql", label = "database,sql,mysql", consumerOnly = true)
public final class DebeziumMySqlEndpoint extends DebeziumEndpoint {

    @UriParam
    private MySqlConnectorEmbeddedDebeziumConfiguration configuration;

    public DebeziumMySqlEndpoint(final String uri, final DebeziumMySqlComponent component, final MySqlConnectorEmbeddedDebeziumConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public DebeziumMySqlEndpoint() {
        super();
    }

    @Override
    public MySqlConnectorEmbeddedDebeziumConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(final MySqlConnectorEmbeddedDebeziumConfiguration configuration) {
        this.configuration = configuration;
    }
}
