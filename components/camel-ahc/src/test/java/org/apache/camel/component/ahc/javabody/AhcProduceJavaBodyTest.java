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
package org.apache.camel.component.ahc.javabody;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ahc.AhcComponent;
import org.apache.camel.component.ahc.AhcConstants;
import org.apache.camel.component.ahc.BaseAhcTest;
import org.apache.camel.component.jetty.JettyHttpComponent;
import org.apache.camel.http.common.HttpCommonComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 *
 */
public class AhcProduceJavaBodyTest extends BaseAhcTest {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testHttpSendJavaBodyAndReceiveString() throws Exception {
        HttpCommonComponent jetty = context.getComponent("jetty", HttpCommonComponent.class);
        jetty.setAllowJavaSerializedObject(true);

        AhcComponent ahc = context.getComponent("ahc", AhcComponent.class);
        ahc.setAllowJavaSerializedObject(true);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getTestServerEndpointUri())
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                MyCoolBean cool = exchange.getIn().getBody(MyCoolBean.class);
                                assertNotNull(cool);

                                assertEquals(123, cool.getId());
                                assertEquals("Camel", cool.getName());

                                // we send back plain test
                                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/plain");
                                exchange.getMessage().setBody("OK");
                            }
                        });
            }
        });
        context.start();

        MyCoolBean cool = new MyCoolBean(123, "Camel");

        String reply = template.requestBodyAndHeader(getAhcEndpointUri(), cool,
                Exchange.CONTENT_TYPE, AhcConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT, String.class);

        assertEquals("OK", reply);
    }

    @Test
    public void testHttpSendJavaBodyAndReceiveJavaBody() throws Exception {
        HttpCommonComponent jetty = context.getComponent("jetty", HttpCommonComponent.class);
        jetty.setAllowJavaSerializedObject(true);

        AhcComponent ahc = context.getComponent("ahc", AhcComponent.class);
        ahc.setAllowJavaSerializedObject(true);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getTestServerEndpointUri())
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                MyCoolBean cool = exchange.getIn().getBody(MyCoolBean.class);
                                assertNotNull(cool);

                                assertEquals(123, cool.getId());
                                assertEquals("Camel", cool.getName());

                                MyCoolBean reply = new MyCoolBean(456, "Camel rocks");
                                exchange.getMessage().setBody(reply);
                                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, AhcConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);
                            }
                        });
            }
        });
        context.start();

        MyCoolBean cool = new MyCoolBean(123, "Camel");

        MyCoolBean reply = template.requestBodyAndHeader(getAhcEndpointUri(), cool,
                Exchange.CONTENT_TYPE, AhcConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT, MyCoolBean.class);

        assertEquals(456, reply.getId());
        assertEquals("Camel rocks", reply.getName());
    }

    @Test
    public void testHttpSendStringAndReceiveJavaBody() throws Exception {
        HttpCommonComponent jetty = context.getComponent("jetty", HttpCommonComponent.class);
        jetty.setAllowJavaSerializedObject(true);

        AhcComponent ahc = context.getComponent("ahc", AhcComponent.class);
        ahc.setAllowJavaSerializedObject(true);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getTestServerEndpointUri())
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String body = exchange.getIn().getBody(String.class);
                                assertNotNull(body);
                                assertEquals("Hello World", body);

                                MyCoolBean reply = new MyCoolBean(456, "Camel rocks");
                                exchange.getMessage().setBody(reply);
                                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, AhcConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);
                            }
                        });
            }
        });
        context.start();

        MyCoolBean reply = template.requestBody(getAhcEndpointUri(), "Hello World", MyCoolBean.class);

        assertEquals(456, reply.getId());
        assertEquals("Camel rocks", reply.getName());
    }

    @Test
    public void testNotAllowedReceive() throws Exception {
        HttpCommonComponent jetty = context.getComponent("jetty", HttpCommonComponent.class);
        jetty.setAllowJavaSerializedObject(true);

        AhcComponent ahc = context.getComponent("ahc", AhcComponent.class);
        ahc.setAllowJavaSerializedObject(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getTestServerEndpointUri())
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String body = exchange.getIn().getBody(String.class);
                                assertNotNull(body);
                                assertEquals("Hello World", body);

                                MyCoolBean reply = new MyCoolBean(456, "Camel rocks");
                                exchange.getMessage().setBody(reply);
                                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, AhcConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);
                            }
                        });
            }
        });
        context.start();

        Object reply = template.requestBody(getAhcEndpointUri(), "Hello World", Object.class);
        MyCoolBean bean = context.getTypeConverter().convertTo(MyCoolBean.class, reply);
        assertNull(bean);
    }

    @Test
    public void testNotAllowed() throws Exception {
        JettyHttpComponent jetty = context.getComponent("jetty", JettyHttpComponent.class);
        jetty.setAllowJavaSerializedObject(false);

        AhcComponent ahc = context.getComponent("ahc", AhcComponent.class);
        ahc.setAllowJavaSerializedObject(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getTestServerEndpointUri())
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String body = exchange.getIn().getBody(String.class);
                                assertNotNull(body);
                                assertEquals("Hello World", body);

                                MyCoolBean reply = new MyCoolBean(456, "Camel rocks");
                                exchange.getMessage().setBody(reply);
                                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, AhcConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);
                            }
                        });
            }
        });
        context.start();

        MyCoolBean cool = new MyCoolBean(123, "Camel");

        try {
            template.requestBodyAndHeader(getAhcEndpointUri(), cool,
                    Exchange.CONTENT_TYPE, AhcConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT, MyCoolBean.class);
            fail("Should fail");
        } catch (Exception e) {
            assertTrue(e.getCause().getMessage().startsWith("Content-type application/x-java-serialized-object is not allowed"));
        }
    }

}
