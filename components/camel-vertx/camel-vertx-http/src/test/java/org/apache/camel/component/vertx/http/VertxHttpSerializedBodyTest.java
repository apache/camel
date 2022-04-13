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
package org.apache.camel.component.vertx.http;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Objects;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.vertx.http.VertxHttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VertxHttpSerializedBodyTest extends VertxHttpTestSupport {

    @Test
    public void testSerializeRequestBody() throws InterruptedException {
        SerializedBean bean = new SerializedBean();
        bean.setName("Mr A Camel");
        bean.setAge(15);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived(bean);

        template.sendBodyAndHeader(getProducerUri() + "/serialized", bean, Exchange.CONTENT_TYPE,
                CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testSerializeRequestBodyNotSerializable() {
        final String endpointUri = getProducerUri() + "/serialized";
        final NotSerializableBean body = new NotSerializableBean();

        assertThrows(CamelExecutionException.class, () -> template.sendBodyAndHeader(endpointUri, body, Exchange.CONTENT_TYPE,
                CONTENT_TYPE_JAVA_SERIALIZED_OBJECT));
    }

    @Test
    public void testSerializeRequestBodyDenied() {
        VertxHttpComponent component = context.getComponent("vertx-http", VertxHttpComponent.class);
        component.setAllowJavaSerializedObject(false);

        final String endpointUri = getProducerUri() + "/serialized";
        final SerializedBean body = new SerializedBean();

        assertThrows(CamelExecutionException.class, () -> template.sendBodyAndHeader(endpointUri, body, Exchange.CONTENT_TYPE,
                CONTENT_TYPE_JAVA_SERIALIZED_OBJECT));
    }

    @Test
    public void testDeserializeResponseBody() {
        SerializedBean bean = template.requestBody(getProducerUri() + "/deserialized", null, SerializedBean.class);
        assertNotNull(bean);
        assertEquals("Mr A Camel", bean.getName());
        assertEquals(15, bean.getAge());
    }

    @Test
    public void testDeserializeResponseBodyDenied() {
        VertxHttpComponent component = context.getComponent("vertx-http", VertxHttpComponent.class);
        component.setAllowJavaSerializedObject(false);
        SerializedBean bean = template.requestBody(getProducerUri() + "/deserialized", null, SerializedBean.class);
        assertNull(bean);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        VertxHttpComponent component = new VertxHttpComponent();
        component.setAllowJavaSerializedObject(true);

        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("vertx-http", component);

        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri() + "/serialized")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // camel-undertow does not (yet) support object deserialization so we do it manually
                                InputStream inputStream = exchange.getContext().getTypeConverter().convertTo(InputStream.class,
                                        exchange.getIn().getBody());
                                if (inputStream != null) {
                                    try {
                                        Object object = VertxHttpHelper.deserializeJavaObjectFromStream(inputStream);
                                        if (object instanceof SerializedBean) {
                                            SerializedBean bean = (SerializedBean) object;
                                            exchange.getMessage().setBody(bean);
                                        }
                                    } finally {
                                        IOHelper.close(inputStream);
                                    }
                                }
                            }
                        })
                        .to("mock:result");

                from(getTestServerUri() + "/deserialized")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                SerializedBean bean = new SerializedBean();
                                bean.setName("Mr A Camel");
                                bean.setAge(15);

                                Message message = exchange.getMessage();
                                message.setHeader(Exchange.CONTENT_TYPE, CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);

                                try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                                    VertxHttpHelper.writeObjectToStream(bos, bean);
                                    message.setBody(bos.toByteArray());
                                }
                            }
                        });
            }
        };
    }

    private static final class SerializedBean implements Serializable {
        private String name;
        private int age;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SerializedBean bean = (SerializedBean) o;
            return age == bean.age && Objects.equals(name, bean.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age);
        }
    }

    private static final class NotSerializableBean {

    }
}
