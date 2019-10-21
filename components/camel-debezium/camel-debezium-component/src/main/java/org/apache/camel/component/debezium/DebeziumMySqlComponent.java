package org.apache.camel.component.debezium;

import org.apache.camel.CamelContext;
import org.apache.camel.component.debezium.configuration.EmbeddedDebeziumConfiguration;
import org.apache.camel.component.debezium.configuration.MySqlConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.spi.annotations.Component;

@Component("debezium-mysql")
public final class DebeziumMySqlComponent extends DebeziumComponent<MySqlConnectorEmbeddedDebeziumConfiguration> {

    private MySqlConnectorEmbeddedDebeziumConfiguration configuration;

    public DebeziumMySqlComponent() {
        super();
    }

    public DebeziumMySqlComponent(final CamelContext context) {
        super(context);
    }

    /**
     * Allow pre-configured Configurations to be set, you will need to extend
     * {@link MySqlConnectorEmbeddedDebeziumConfiguration} in order to create the configuration
     * for the component
     *
     * @return {@link MySqlConnectorEmbeddedDebeziumConfiguration}
     */
    @Override
    public MySqlConnectorEmbeddedDebeziumConfiguration getConfiguration() {
        if (configuration == null) {
            return new MySqlConnectorEmbeddedDebeziumConfiguration();
        }
        return configuration;
    }

    @Override
    public void setConfiguration(MySqlConnectorEmbeddedDebeziumConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected DebeziumEndpoint initializeDebeziumEndpoint(String uri, MySqlConnectorEmbeddedDebeziumConfiguration configuration) {
        return new DebeziumMySqlEndpoint(uri, this,  configuration);
    }
}
