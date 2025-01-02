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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SmbProducerFileExistAppendIT extends SmbServerTestSupport {

    //    private static final boolean ON_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    protected String getSmbUrl() {
        return String.format(
                "smb2:%s/%s?username=%s&password=%s&path=/exist&delay=2000&noop=true&fileExist=Append",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @BeforeEach
    public void sendMessages() {
        template.sendBodyAndHeader(getSmbUrl(), "Hello World\n", Exchange.FILE_NAME, "hello.txt");
    }

    @Test
    public void testAppend() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        String expectBody = "Hello World\nBye World";

        mock.expectedBodiesReceived(expectBody);

        template.sendBodyAndHeader(getSmbUrl(), "Bye World", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint.assertIsSatisfied(context);

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Hello World\nBye World",
                        new String(copyFileContentFromContainer("/data/rw/exist/hello.txt"))));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getSmbUrl()).to("mock:result");
            }
        };
    }
}
