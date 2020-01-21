package org.apache.camel.builder.component.dsl;

import org.apache.camel.Component;
import org.apache.camel.builder.component.AbstractComponentBuilder;
import org.apache.camel.builder.component.ComponentBuilder;
import org.apache.camel.component.kafka.KafkaComponent;

public interface KafkaComponentBuilderFactory {
    static KafkaComponentBuilder kafka() {
        return new KafkaComponentBuilderImpl();
    }

    interface KafkaComponentBuilder extends ComponentBuilder {
        default KafkaComponentBuilder withComponentName(String name) {
            doSetComponentName(name);
            return this;
        }
        default KafkaComponentBuilder setConfiguration(
                org.apache.camel.component.kafka.KafkaConfiguration configuration) {
            doSetProperty("configuration", configuration);
            return this;
        }
        default KafkaComponentBuilder setBrokers(java.lang.String brokers) {
            doSetProperty("brokers", brokers);
            return this;
        }
        default KafkaComponentBuilder setWorkerPool(
                java.util.concurrent.ExecutorService workerPool) {
            doSetProperty("workerPool", workerPool);
            return this;
        }
        default KafkaComponentBuilder setUseGlobalSslContextParameters(
                boolean useGlobalSslContextParameters) {
            doSetProperty("useGlobalSslContextParameters", useGlobalSslContextParameters);
            return this;
        }
        default KafkaComponentBuilder setBreakOnFirstError(
                boolean breakOnFirstError) {
            doSetProperty("breakOnFirstError", breakOnFirstError);
            return this;
        }
        default KafkaComponentBuilder setAllowManualCommit(
                boolean allowManualCommit) {
            doSetProperty("allowManualCommit", allowManualCommit);
            return this;
        }
        default KafkaComponentBuilder setKafkaManualCommitFactory(
                org.apache.camel.component.kafka.KafkaManualCommitFactory kafkaManualCommitFactory) {
            doSetProperty("kafkaManualCommitFactory", kafkaManualCommitFactory);
            return this;
        }
        default KafkaComponentBuilder setBasicPropertyBinding(
                boolean basicPropertyBinding) {
            doSetProperty("basicPropertyBinding", basicPropertyBinding);
            return this;
        }
        default KafkaComponentBuilder setLazyStartProducer(
                boolean lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
        default KafkaComponentBuilder setBridgeErrorHandler(
                boolean bridgeErrorHandler) {
            doSetProperty("bridgeErrorHandler", bridgeErrorHandler);
            return this;
        }
    }

    class KafkaComponentBuilderImpl
            extends
            AbstractComponentBuilder
            implements
            KafkaComponentBuilder {
        public KafkaComponentBuilderImpl() {
            super("kafka");
        }
        @Override
        protected Component buildConcreteComponent() {
            return new KafkaComponent();
        }
    }
}
