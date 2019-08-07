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

import java.io.File;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ZipFileIteratorDataFormatTest extends CamelTestSupport {

    @Test
    public void testZipFileIterator() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        Stream body = Stream.of("ABC", "DEF", "1234567890");
        template.sendBody("direct:zip", body);

        assertMockEndpointsSatisfied();

        resetMocks();

        // unzip the file
        getMockEndpoint("mock:unzip").expectedBodiesReceived("ABCDEF1234567890");

        File file = new File("target/output/report.txt.zip");
        template.sendBody("direct:unzip", file);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:zip")
                    .setHeader(Exchange.FILE_NAME, constant("report.txt"))
                    .marshal().zipFile()
                    .to("file:target/output")
                    .to("mock:result");

                from("direct:unzip")
                    .unmarshal().zipFile()
                    .to("mock:unzip");
            }
        };
    }
}
