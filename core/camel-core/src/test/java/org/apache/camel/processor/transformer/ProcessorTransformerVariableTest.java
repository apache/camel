/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor.transformer;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessorTransformerVariableTest {

    @Test
    void testVariablesCopiedToTransformExchange() throws Exception {
        try (CamelContext context = new org.apache.camel.impl.DefaultCamelContext()) {
            context.start();

            ProcessorTransformer transformer = new ProcessorTransformer(context);
            transformer.setProcessor(exchange -> {
                String varValue = exchange.getVariable("myVar", String.class);
                exchange.getMessage().setBody("transformed:" + varValue);
            });
            transformer.doInit();
            transformer.doStart();

            Exchange exchange = new DefaultExchange(context);
            exchange.setVariable("myVar", "Hello from variable");
            exchange.getMessage().setBody("input");

            transformer.transform(exchange.getMessage(), new DataType("custom:input"), new DataType("custom:output"));

            assertThat(exchange.getMessage().getBody(String.class)).isEqualTo("transformed:Hello from variable");
        }
    }

    @Test
    void testExchangePropertiesCopiedToTransformExchange() throws Exception {
        try (CamelContext context = new org.apache.camel.impl.DefaultCamelContext()) {
            context.start();

            ProcessorTransformer transformer = new ProcessorTransformer(context);
            transformer.setProcessor(exchange -> {
                String propValue = exchange.getProperty("myProp", String.class);
                exchange.getMessage().setBody("transformed:" + propValue);
            });
            transformer.doInit();
            transformer.doStart();

            Exchange exchange = new DefaultExchange(context);
            exchange.setProperty("myProp", "Hello from property");
            exchange.getMessage().setBody("input");

            transformer.transform(exchange.getMessage(), new DataType("custom:input"), new DataType("custom:output"));

            assertThat(exchange.getMessage().getBody(String.class)).isEqualTo("transformed:Hello from property");
        }
    }
}
