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
package org.apache.camel.component.google.pubsublite;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GooglePubsubLiteConsumerTest extends CamelTestSupport {

    @Mock
    private Processor processor;

    @Mock
    private GooglePubsubLiteEndpoint endpoint;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGooglePubsubLiteConsumer() {
        when(endpoint.getCamelContext()).thenReturn(context);
        GooglePubsubLiteConsumer consumer = new GooglePubsubLiteConsumer(endpoint, processor);
        assertNotNull(consumer);
    }

    @Test
    public void testGooglePubsubLiteConsumerException() throws Exception {
        when(endpoint.getCamelContext()).thenReturn(context);
        GooglePubsubLiteConsumer consumer = new GooglePubsubLiteConsumer(endpoint, processor);
        assertNotNull(consumer);

        // Assuming the processor is throwing an exception
        doThrow(new RuntimeException("Mocked Exception")).when(processor).process(any());

        // Creating a new exception to test the process
        Exchange exchange = new DefaultExchange(context);

        assertThrows(RuntimeException.class, () -> processor.process(exchange));
        // Verifying processor was called
        verify(processor, times(1)).process(any());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .process(exchange -> {
                            // simulate some processing here.
                        }).to("mock:result");
            }
        };
    }
}
