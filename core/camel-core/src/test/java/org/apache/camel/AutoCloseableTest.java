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
package org.apache.camel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutoCloseableTest {

    @Test
    public void servicesShouldBeAutoCloseable() throws Exception {
        CamelContext usedContext = null;
        ProducerTemplate usedProducer = null;

        try (CamelContext context = new DefaultCamelContext();
             ProducerTemplate producer = context.createProducerTemplate()) {
            usedContext = context;
            usedProducer = producer;

            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    from("direct:start").log("hello ${body}");
                }
            });
            context.start();

            producer.sendBody("direct:start", "word");
        }

        assertThat(usedContext.isStopped()).isTrue();
        assertThat(((DefaultProducerTemplate) usedProducer).isStopped()).isTrue();
    }

}
