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
package org.apache.camel.component.file;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

public class FileConsumerIdempotentKeyChangedIssueTest extends ContextTestSupport {

    private Endpoint endpoint;

    public void testFile() throws Exception {
        getMockEndpoint("mock:file").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(endpoint, "Hello World", Exchange.FILE_NAME, "hello.txt");

        context.startAllRoutes();

        assertMockEndpointsSatisfied();
        oneExchangeDone.matches(5, TimeUnit.SECONDS);

        resetMocks();
        getMockEndpoint("mock:file").expectedBodiesReceived("Hello World Again");

        template.sendBodyAndHeader(endpoint, "Hello World Again", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                endpoint = endpoint("file:target/changed?noop=true&readLock=changed&initialDelay=0&delay=10&readLockCheckInterval=100"
                        + "&idempotentKey=${file:onlyname}-${file:size}-${date:file:yyyyMMddHHmmss}");

                from(endpoint).noAutoStartup()
                    .convertBodyTo(String.class)
                    .to("log:file")
                    .to("mock:file");
            }
        };
    }
}
