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
package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that stream caching does not throw {@link NullPointerException} when the message body is a
 * {@link WrappedFile} whose embedded content has not been loaded (body is null), and that the per-exchange WARN
 * deduplication flag is set (preventing duplicate WARNs on multi-node routes). CAMEL-24563.
 */
public class GenericFileStreamCachingNullBodyTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setStreamCaching(true);
                // multi-node route: StreamCachingAdvice fires before each node —
                // without the exchange-property guard the WARN would fire multiple times
                from("direct:start")
                        .to("log:step1")
                        .to("log:step2")
                        .to("mock:result");
            }
        };
    }

    private WrappedFile<Object> unloadedFile() {
        return new WrappedFile<>() {
            @Override
            public Object getFile() {
                return null;
            }

            @Override
            public Object getBody() {
                return null;
            }

            @Override
            public long getFileLength() {
                return -1;
            }
        };
    }

    @Test
    void testWrappedFileWithNullBodyDoesNotThrowNPE() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        assertDoesNotThrow(() -> template.sendBody("direct:start", unloadedFile()),
                "Stream caching must not throw NPE when WrappedFile body is null");

        assertMockEndpointsSatisfied();

        Exchange received = mock.getReceivedExchanges().get(0);
        assertNotNull(received.getIn().getBody(), "Body should still be present when stream caching is skipped");
    }

    @Test
    void testWarnDeduplicationFlagSetOnExchange() throws Exception {
        // When a WrappedFile with null body flows through a multi-node route, the
        // exchange property CamelStreamCacheWarnedWrappedFileNullBody should be set
        // after the first node — this prevents duplicate WARNs on subsequent nodes.
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", unloadedFile());

        assertMockEndpointsSatisfied();

        Exchange received = mock.getReceivedExchanges().get(0);
        Object flag = received.getProperty("CamelStreamCacheWarnedWrappedFileNullBody");
        assertThat(flag)
                .as("Exchange property must be set to prevent duplicate WARN logs per exchange")
                .isEqualTo(Boolean.TRUE);
    }
}
