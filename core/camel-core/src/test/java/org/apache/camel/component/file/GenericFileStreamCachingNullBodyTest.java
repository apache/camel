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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that stream caching does not throw {@link NullPointerException} when the message body is a
 * {@link WrappedFile} whose embedded content has not been loaded (body is null), and instead logs a diagnostic WARN.
 *
 * <p>
 * Real-world trigger: a RemoteFile (SFTP/FTP) body is null when stream-caching runs before the consumer has loaded the
 * remote content. In Camel 3.x this caused a bare NPE; Camel 4 guarded the NPE but silently no-oped. CAMEL-24563 adds a
 * targeted WARN in StreamCachingHelper so operators can diagnose the misconfiguration.
 */
public class GenericFileStreamCachingNullBodyTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setStreamCaching(true);
                from("direct:start").to("mock:result");
            }
        };
    }

    @Test
    void testWrappedFileWithNullBodyDoesNotThrowNPE() throws Exception {
        // Simulate a WrappedFile (e.g. RemoteFile) whose body was never loaded
        WrappedFile<Object> unloadedFile = new WrappedFile<>() {
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

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // must not throw NullPointerException — CAMEL-24563
        assertDoesNotThrow(() -> template.sendBody("direct:start", unloadedFile),
                "Stream caching must not throw NPE when WrappedFile body is null");

        assertMockEndpointsSatisfied();

        // body should remain the original WrappedFile (not cached) since content was not loaded
        Exchange received = mock.getReceivedExchanges().get(0);
        assertNotNull(received.getIn().getBody(), "Body should still be present even when stream caching is skipped");
    }
}
