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
package org.apache.camel.dataformat.zipfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.ZipFileDataFormat;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class ZipSplitterUseOriginalMessageTest extends CamelTestSupport {

    private List<String> list1 = new ArrayList<>();
    private List<String> list2 = new ArrayList<>();

    @Test
    public void testSplitter() throws InterruptedException {
        MockEndpoint processZipEntry = getMockEndpoint("mock:processZipEntry");
        processZipEntry.expectedBodiesReceivedInAnyOrder("chau", "hi", "hola", "another_chiau", "another_hi");
        MockEndpoint.assertIsSatisfied(context);

        // should be the same
        Arrays.deepEquals(list1.toArray(), list2.toArray());
    }

    private org.apache.camel.model.dataformat.ZipFileDataFormat multiEntryZipFormat() {
        var zipFormat = new ZipFileDataFormat();
        zipFormat.setUsingIterator("true");
        return zipFormat;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class)
                        // turn on original message which caused CAMEL-19670
                        .useOriginalMessage();

                // Unzip file and Split it according to FileEntry
                from("file:src/test/resources/org/apache/camel/dataformat/zipfile/data?delay=1000&noop=true")
                        .log("Start processing big file: ${header.CamelFileName}")
                        .unmarshal(multiEntryZipFormat())
                        .split(bodyAs(Iterator.class)).streaming()
                        .log("${body}")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // should be able to read the stream
                                String s = exchange.getMessage().getBody(String.class);
                                list1.add(s);
                            }
                        })
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // should be able to read the stream again
                                String s = exchange.getMessage().getBody(String.class);
                                list2.add(s);
                            }
                        })
                        .to("mock:processZipEntry")
                        .end()
                        .log("Done processing big file: ${header.CamelFileName}");
            }
        };

    }

}
