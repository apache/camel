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
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.tracing.MockSpanAdapter;
import org.apache.camel.tracing.TagConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Deprecated(since = "4.19.0")
public class AbstractHttpSpanDecoratorTest {

    private static final String TEST_URI = "http://localhost:8080/test";

    private AbstractHttpSpanDecorator decorator;

    @BeforeEach
    public void before() {
        this.decorator = new AbstractHttpSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };
    }

    @Test
    public void testGetOperationName() {
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_METHOD)).thenReturn("PUT");
        Mockito.when(endpoint.getEndpointUri()).thenReturn("http://localhost:8080/endpoint");

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

        Mockito.when(endpoint.getEndpointUri()).thenReturn("http://localhost:8080/endpoint");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_URI)).thenReturn(TEST_URI);

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

        Mockito.when(endpoint.getEndpointUri()).thenReturn("http://localhost:8080/endpoint");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_URI)).thenReturn("Another URL");
        Mockito.when(message.getHeader(Exchange.HTTP_URL)).thenReturn(TEST_URI);

        assertEquals(TEST_URI, decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testGetHttpURLFromHeaderUri() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("http://localhost:8080/endpoint");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_URI)).thenReturn(TEST_URI);

        assertEquals(TEST_URI, decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testGetHttpURLFromHeaderUrlPresentedLikeCamelUri() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI);
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_URL)).thenReturn("http://scott:tiger@localhost:8080/test");

        assertEquals("http://scott:xxxxxx@localhost:8080/test", decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testGetHttpURLFromEndpointUri() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI);
        Mockito.when(exchange.getIn()).thenReturn(message);

        assertEquals(TEST_URI, decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testGetHttpURLFromEndpointUriWithAdditionalScheme() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("netty-http:" + TEST_URI);
        Mockito.when(exchange.getIn()).thenReturn(message);

        assertEquals(TEST_URI, decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testGetHttpURLFromEndpointUriWithoutEndpointOptions() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI + "?connectTimeout=5000&throwExceptionOnFailure=false");
        Mockito.when(exchange.getIn()).thenReturn(message);

        assertEquals(TEST_URI, decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testGetHttpURLFromEndpointUriWithComponentSchemeAndHttpsAddress() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("vertx-http://https://localhost:8443/test?connectTimeout=5000");
        Mockito.when(exchange.getIn()).thenReturn(message);

        assertEquals("https://localhost:8443/test", decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testGetHttpURLFromServiceUrl() {
        Endpoint endpoint = Mockito.mock(Endpoint.class, Mockito.withSettings().extraInterfaces(EndpointServiceLocation.class));
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("https://localhost:8443/test?q=1&connectTimeout=5000");
        Mockito.when(((EndpointServiceLocation) endpoint).getServiceUrl()).thenReturn("https://localhost:8443/test?q=1");
        Mockito.when(exchange.getIn()).thenReturn(message);

        assertEquals("https://localhost:8443/test?q=1", decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testGetHttpURLFromEndpointUriWhenServiceUrlIsNotHttpAddress() {
        Endpoint endpoint = Mockito.mock(Endpoint.class, Mockito.withSettings().extraInterfaces(EndpointServiceLocation.class));
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("netty-http:" + TEST_URI);
        Mockito.when(((EndpointServiceLocation) endpoint).getServiceUrl()).thenReturn("http:localhost:8080");
        Mockito.when(exchange.getIn()).thenReturn(message);

        assertEquals(TEST_URI, decorator.getHttpURL(exchange, endpoint));
    }

    @Test
    public void testPreHttpsEndpoint() {
        Endpoint endpoint = Mockito.mock(Endpoint.class, Mockito.withSettings().extraInterfaces(EndpointServiceLocation.class));
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("https://localhost:8443/test?connectTimeout=5000");
        Mockito.when(((EndpointServiceLocation) endpoint).getServiceUrl()).thenReturn("https://localhost:8443/test");
        Mockito.when(exchange.getIn()).thenReturn(message);

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.pre(span, exchange, endpoint);

        assertEquals("https://localhost:8443/test", span.tags().get(TagConstants.HTTP_URL));
    }

    @Test
    public void testPostResponseCode() {
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getMessage()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).thenReturn(200);

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.post(span, exchange, null);

        assertEquals(200, span.tags().get(TagConstants.HTTP_STATUS));
    }

}
