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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class FileConsumerFileNameFilterTest extends ContextTestSupport {

    private final String fileUri = fileUri();

    @Test
    public void testFileConsumer() throws Exception {
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:txt");
        mockEndpoint.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");

        template.sendBodyAndHeader(fileUri, "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(fileUri, "<customer>123</customer>", Exchange.FILE_NAME, "customer.xml");
        template.sendBodyAndHeader(fileUri, "<book>Camel Rocks</book>", Exchange.FILE_NAME, "book.xml");
        template.sendBodyAndHeader(fileUri, "Bye World", Exchange.FILE_NAME, "bye.txt");

        context.getRouteController().startAllRoutes();

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10&fileName=${file:onlyname.noext}.txt")).noAutoStartup()
                        .to("mock:txt");
            }
        };
    }
}
