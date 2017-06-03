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
package org.apache.camel.dataformat.tarfile;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class TarSplitterRouteTest extends CamelTestSupport {

    @Test
    public void testSplitter() throws InterruptedException {
        MockEndpoint processTarEntry = getMockEndpoint("mock:processTarEntry");

        processTarEntry.expectedBodiesReceivedInAnyOrder("chau", "hi", "hola", "hello", "greetings");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Untar file and Split it according to FileEntry
                from("file:src/test/resources/org/apache/camel/dataformat/tarfile/data?consumer.delay=1000&noop=true")
                    .log("Start processing big file: ${header.CamelFileName}")
                    .split(new TarSplitter()).streaming()
                        .convertBodyTo(String.class).to("mock:processTarEntry")
                        .to("log:entry")
                    .end()
                    .log("Done processing big file: ${header.CamelFileName}");
            }
        };

    }

}
