/**
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
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class MultipleConsumersSupportTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

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
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (FailedToStartRouteException e) {
            assertTrue(e.getMessage().endsWith("Multiple consumers for the same endpoint is not allowed: my:endpoint"));
        }
    }

    public void testYesMultipleConsumersSupport() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                MyOtherEndpoint my = new MyOtherEndpoint();

                from(my).to("mock:a");
                from(my).to("mock:b");
            }
        });
        // this one is allowing multiple consumers on the same endpoint so no problem starting
        context.start();
        context.stop();
    }

    private static class MyEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

        public boolean isSingleton() {
            return true;
        }

        public boolean isMultipleConsumersSupported() {
            return false;
        }

        public Producer createProducer() throws Exception {
            return null;
        }

        public Consumer createConsumer(Processor processor) throws Exception {
            return new DefaultConsumer(this, processor);
        }

        @Override
        protected String createEndpointUri() {
            return "MyEndpoint";
        }
    }

    private static class MyOtherEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

        public boolean isSingleton() {
            return true;
        }

        public boolean isMultipleConsumersSupported() {
            return true;
        }

        public Producer createProducer() throws Exception {
            return null;
        }

        public Consumer createConsumer(Processor processor) throws Exception {
            return new DefaultConsumer(this, processor);
        }

        @Override
        protected String createEndpointUri() {
            return "MyOtherEndpoint";
        }
    }
}
