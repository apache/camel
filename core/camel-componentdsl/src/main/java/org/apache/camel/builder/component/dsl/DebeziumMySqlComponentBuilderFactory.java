package org.apache.camel.builder.component.dsl;

import org.apache.camel.Component;
import org.apache.camel.builder.component.AbstractComponentBuilder;
import org.apache.camel.builder.component.ComponentBuilder;
import org.apache.camel.component.debezium.DebeziumMySqlComponent;
import org.apache.camel.component.debezium.configuration.MySqlConnectorEmbeddedDebeziumConfiguration;

public class DebeziumMySqlComponentBuilderFactory {
    public interface DebeziumMySqlComponentBuilder extends ComponentBuilder {
        default DebeziumMySqlComponentBuilder withComponentName(String name) {
            doSetComponentName(name);
            return this;
        }
        default DebeziumMySqlComponentBuilder setConfiguration(MySqlConnectorEmbeddedDebeziumConfiguration configuration) {
            doSetProperty("configuration", configuration);
            return this;
        }

        default DebeziumMySqlComponentBuilder setBasicPropertyBinding(boolean basicPropertyBinding) {
            doSetProperty("basicPropertyBinding", basicPropertyBinding);
            return this;
        }
    }

    public static DebeziumMySqlComponentBuilder debeziumMysql() {
        class DebeziumMySqlComponentBuilderImpl extends AbstractComponentBuilder implements DebeziumMySqlComponentBuilder {
            public DebeziumMySqlComponentBuilderImpl() {
                super("debezium-mysql");
            }
            @Override
            protected Component buildConcreteComponent() {
                return new DebeziumMySqlComponent();
            }
        }
        return new DebeziumMySqlComponentBuilderImpl();
    }
}