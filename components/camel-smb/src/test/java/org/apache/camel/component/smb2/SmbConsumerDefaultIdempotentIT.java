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
package org.apache.camel.component.smb2;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SmbConsumerDefaultIdempotentIT extends SmbServerTestSupport {

    protected String getSmbUrl() {
        return String.format(
                "smb2:%s/%s?username=%s&password=%s&path=/defidemp&idempotent=true&delete=true&delay=1000",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Test
    public void testIdempotent() throws Exception {
        // consume the file the first time
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedMessageCount(1);

        sendFile(getSmbUrl(), "Hello World", "report.txt");

        MockEndpoint.assertIsSatisfied(context);

        Thread.sleep(100L);

        mock.reset();
        mock.expectedMessageCount(0);

        // move file back
        sendFile(getSmbUrl(), "Hello World", "report.txt");

        // consumer shouldn't consume file a second time
        Thread.sleep(2000L);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                from(getSmbUrl())
                        .to("mock:result");
            }
        };
    }
}
