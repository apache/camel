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

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileMoveWithInMessageTest extends ContextTestSupport {

    @Test
    public void testMove() throws Exception {
        String uri = fileUri();
        template.sendBodyAndHeader(uri, "Hello World1", Exchange.FILE_NAME, "hello1.txt");
        template.sendBodyAndHeader(uri, "Hello World2", Exchange.FILE_NAME, "hello2.txt");

        // trigger
        template.sendBody("seda:triggerIn", "");

        File file1 = new File(testDirectory().toFile(), "archive/hello1.txt");
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(file1.exists(), "The file should exist in the archive folder"));

        File file2 = new File(testDirectory().toFile(), "archive/hello2.txt");
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(file2.exists(), "The file should exist in the archive folder"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:triggerIn")
                        .pollEnrich(fileUri() + "?move=archive")
                        .pollEnrich(fileUri() + "?move=archive")
                        .process(new TestProcessor());
            }
        };
    }

    private static class TestProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            DefaultMessage msg = new DefaultMessage(exchange);
            msg.setBody(exchange.getIn().getBody());
            msg.setHeaders(exchange.getIn().getHeaders());
            exchange.setIn(msg);
        }
    }
}
