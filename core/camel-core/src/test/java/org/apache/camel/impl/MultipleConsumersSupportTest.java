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
package org.apache.camel.impl;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultipleConsumersSupportTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testNotMultipleConsumersSupport() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                MyEndpoint my = new MyEndpoint();
                my.setCamelContext(context);
                my.setEndpointUriIfNotSpecified("my:endpoint");

                from(my).to("mock:a");

                from("direct:start").to("mock:result");

                from(my).to("mock:b");
            }
        });

        Exception e = assertThrows(Exception.class, () -> context.start(), "Should have thrown exception");
        assertTrue(e.getMessage().endsWith("Multiple consumers for the same endpoint is not allowed: my:endpoint"));
    }

    @Test
    public void testYesMultipleConsumersSupport() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                MyOtherEndpoint my = new MyOtherEndpoint();
                my.setCamelContext(context);

                from(my).to("mock:a");
                from(my).to("mock:b");
            }
        });
        // this one is allowing multiple consumers on the same endpoint so no
        // problem starting
        context.start();
        context.stop();
    }

    private static class MyEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

        @Override
        public boolean isSingleton() {
            return true;
        }

        @Override
        public boolean isMultipleConsumersSupported() {
            return false;
        }

        @Override
        public Producer createProducer() throws Exception {
            return null;
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return new DefaultConsumer(this, processor);
        }

        @Override
        protected String createEndpointUri() {
            return "MyEndpoint";
        }
    }

    private static class MyOtherEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

        @Override
        public boolean isSingleton() {
            return true;
        }

        @Override
        public boolean isMultipleConsumersSupported() {
            return true;
        }

        @Override
        public Producer createProducer() throws Exception {
            return null;
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return new DefaultConsumer(this, processor);
        }

        @Override
        protected String createEndpointUri() {
            return "MyOtherEndpoint";
        }
    }
}
