package org.apache.camel.builder.component.dsl;

import org.apache.camel.builder.component.ComponentBuilder;
import org.apache.camel.builder.component.ComponentBuilderFactory;
import org.junit.Test;

import static org.junit.Assert.*;

public class DebeziumMySqlComponentBuilderFactoryTest {
    @Test
    public void test() {
        ComponentBuilderFactory.kafka();
    }
}