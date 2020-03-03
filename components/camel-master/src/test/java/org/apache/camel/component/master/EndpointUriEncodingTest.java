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
package org.apache.camel.component.master;

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.cluster.FileLockClusterService;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class EndpointUriEncodingTest extends CamelTestSupport {

    @Test
    public void test() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("foo").isEqualTo("hello} world");
        mock.message(0).header("bar").isEqualTo("hello}+world");
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        setupClusterService();
        return new RouteBuilder() {
            public void configure() {
                context.addComponent("dummy", new DummyComponent());
                from("master:test:dummy://path?foo=hello}+world&bar=RAW(hello}+world)")
                    .to("mock:result");
            }
        };
    }

    private void setupClusterService() throws Exception {
        FileLockClusterService fileLockClusterService = new FileLockClusterService();
        fileLockClusterService.setRoot(System.getProperty("java.io.tmpdir"));
        context().addService(fileLockClusterService);
    }

    private class DummyComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new DefaultEndpoint(uri, this) {
                private String foo;
                private String bar;

                public void setFoo(String foo) {
                    this.foo = foo;
                }

                public void setBar(String bar) {
                    this.bar = bar;
                }

                @Override
                public Producer createProducer() {
                    return null;
                }

                @Override
                public Consumer createConsumer(Processor processor) {
                    return new DefaultConsumer(this, processor) {
                        @Override
                        public void start() {
                            Exchange exchange = createExchange();
                            exchange.getMessage().setHeader("foo", foo);
                            exchange.getMessage().setHeader("bar", bar);
                            try {
                                getProcessor().process(exchange);
                            } catch (Exception e) {
                            }
                        }
                    };
                }
            };
        }
    }

}
