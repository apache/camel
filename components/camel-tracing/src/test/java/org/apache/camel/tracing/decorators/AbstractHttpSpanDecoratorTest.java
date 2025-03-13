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
package org.apache.camel.tracing.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.tracing.MockSpanAdapter;
import org.apache.camel.tracing.SpanDecorator;
import org.apache.camel.tracing.TagConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractHttpSpanDecoratorTest {

    private static final String TEST_URI = "http://localhost:8080/test";

    private HttpSpanDecorator decorator;

    @BeforeEach
    public void before() {
        this.decorator = new HttpSpanDecorator();
    }

    @Test
    public void testGetOperationName() {
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_METHOD)).thenReturn("PUT");
        Mockito.when(endpoint.getEndpointUri()).thenReturn("http://localhost:8080/endpoint");

        SpanDecorator decorator = new AbstractHttpSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };

        assertEquals("PUT", decorator.getOperationName(exchange, endpoint));
    }

    @Test
    public void testGetMethodFromMethodHeader() {
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(endpoint.getEndpointUri()).thenReturn("http://localhost:8080/endpoint");
        Mockito.when(message.getHeader(Exchange.HTTP_METHOD)).thenReturn("PUT");

        assertEquals("PUT", decorator.getHttpMethod(exchange, endpoint));
    }

    @Test
    public void testGetMethodFromMethodHeaderEnum() {
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(endpoint.getEndpointUri()).thenReturn("http://localhost:8080/endpoint");
        Mockito.when(message.getHeader(Exchange.HTTP_METHOD)).thenReturn(HttpMethods.GET);

        assertEquals("GET", decorator.getHttpMethod(exchange, endpoint));
    }

    @Test
    public void testGetMethodQueryStringHeader() {
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(endpoint.getEndpointUri()).thenReturn("http://localhost:8080/endpoint");
        Mockito.when(message.getHeader(Exchange.HTTP_QUERY)).thenReturn("MyQuery");

        assertEquals(AbstractHttpSpanDecorator.GET_METHOD,
                decorator.getHttpMethod(exchange, endpoint));
    }

    @Test
    public void testGetMethodQueryStringInEndpoint() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("http://localhost:8080/endpoint?query=hello");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_URI, String.class))
                .thenReturn("http://localhost:8080/endpoint?query=hello");

        assertEquals(AbstractHttpSpanDecorator.GET_METHOD,
                decorator.getHttpMethod(exchange, endpoint));
    }

    @Test
    public void testGetMethodBodyNotNull() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI);
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_URI, String.class)).thenReturn(TEST_URI);
        Mockito.when(message.getBody()).thenReturn("Message Body");

        assertEquals(AbstractHttpSpanDecorator.POST_METHOD,
                decorator.getHttpMethod(exchange, endpoint));
    }

    @Test
    public void testGetMethodDefault() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI);
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_URI)).thenReturn(TEST_URI);

        assertEquals(AbstractHttpSpanDecorator.GET_METHOD,
                decorator.getHttpMethod(exchange, endpoint));
    }

    @Test
    public void testPreUri() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI);
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_URI, String.class)).thenReturn(TEST_URI);

        SpanDecorator decorator = new AbstractHttpSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.pre(span, exchange, endpoint);

        assertEquals(TEST_URI, span.tags().get(TagConstants.HTTP_URL));
        assertTrue(span.tags().containsKey(TagConstants.HTTP_METHOD));
    }

    @Test
    public void testGetHttpURLFromHeaderUrl() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI);
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_URI, String.class)).thenReturn("Another URL");
        Mockito.when(message.getHeader(Exchange.HTTP_URL, String.class)).thenReturn(TEST_URI);

        AbstractHttpSpanDecorator decorator = new AbstractHttpSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };

        assertEquals(TEST_URI, decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testGetHttpURLFromHeaderUri() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI);
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_URI, String.class)).thenReturn(TEST_URI);

        AbstractHttpSpanDecorator decorator = new AbstractHttpSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };

        assertEquals(TEST_URI, decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testGetHttpURLFromEndpointUri() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI);
        Mockito.when(exchange.getIn()).thenReturn(message);

        AbstractHttpSpanDecorator decorator = new AbstractHttpSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };

        assertEquals(TEST_URI, decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testGetHttpURLFromEndpointUriWithAdditionalScheme() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("netty-http:" + TEST_URI);
        Mockito.when(exchange.getIn()).thenReturn(message);

        AbstractHttpSpanDecorator decorator = new AbstractHttpSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };

        assertEquals(TEST_URI, decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testPostResponseCode() {
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getMessage()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).thenReturn(200);

        SpanDecorator decorator = new AbstractHttpSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.post(span, exchange, null);

        assertEquals(200, span.tags().get(TagConstants.HTTP_STATUS));
    }

    @Test
    public void testMethodInHttpMethodParam() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("http://localhost:8080/endpoint?httpMethod=POST");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_URI, String.class))
                .thenReturn("http://localhost:8080/endpoint?httpMethod=POST");

        assertEquals(AbstractHttpSpanDecorator.POST_METHOD,
                decorator.getHttpMethod(exchange, endpoint));
    }

    @Test
    public void testMethodInHttpMethodParamUsingHeader() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("http://localhost:8080/endpoint?httpMethod=POST");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_METHOD)).thenReturn(HttpMethods.GET);
        Mockito.when(message.getHeader(Exchange.HTTP_URI, String.class))
                .thenReturn("http://localhost:8080/endpoint?httpMethod=POST");

        assertEquals(AbstractHttpSpanDecorator.POST_METHOD,
                decorator.getHttpMethod(exchange, endpoint));
    }

}
