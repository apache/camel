package org.apache.camel.builder.component;

public interface ComponentBuilderFactory {

    static org.apache.camel.builder.component.dsl.DebeziumMySqlComponentBuilderFactory.DebeziumMySqlComponentBuilder debeziumMysql() {
        return org.apache.camel.builder.component.dsl.DebeziumMySqlComponentBuilderFactory.debeziumMysql();
    }
    static org.apache.camel.builder.component.dsl.KafkaComponentBuilderFactory.KafkaComponentBuilder kafka() {
        return org.apache.camel.builder.component.dsl.KafkaComponentBuilderFactory.kafka();
    }
}