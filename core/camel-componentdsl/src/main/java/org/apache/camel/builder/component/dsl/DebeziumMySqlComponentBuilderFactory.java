package org.apache.camel.builder.component.dsl;

import org.apache.camel.Component;
import org.apache.camel.builder.component.AbstractComponentBuilder;
import org.apache.camel.builder.component.ComponentBuilder;
import org.apache.camel.component.debezium.DebeziumMySqlComponent;

public interface DebeziumMySqlComponentBuilderFactory {

    static DebeziumMySqlComponentBuilder debeziumMysql() {
        return new DebeziumMySqlComponentBuilderImpl();
    }

    interface DebeziumMySqlComponentBuilder extends ComponentBuilder {
        default DebeziumMySqlComponentBuilder withComponentName(String name) {
            doSetComponentName(name);
            return this;
        }
        default DebeziumMySqlComponentBuilder setConfiguration(
                org.apache.camel.component.debezium.configuration.MySqlConnectorEmbeddedDebeziumConfiguration configuration) {
            doSetProperty("configuration", configuration);
            return this;
        }
        default DebeziumMySqlComponentBuilder setBasicPropertyBinding(
                boolean basicPropertyBinding) {
            doSetProperty("basicPropertyBinding", basicPropertyBinding);
            return this;
        }
        default DebeziumMySqlComponentBuilder setBridgeErrorHandler(
                boolean bridgeErrorHandler) {
            doSetProperty("bridgeErrorHandler", bridgeErrorHandler);
            return this;
        }
    }

    class DebeziumMySqlComponentBuilderImpl
            extends
                AbstractComponentBuilder
            implements
                DebeziumMySqlComponentBuilder {
        public DebeziumMySqlComponentBuilderImpl() {
            super("debezium-mysql");
        }
        @Override
        protected Component buildConcreteComponent() {
            return new DebeziumMySqlComponent();
        }
    }
}