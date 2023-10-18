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
package org.apache.camel.component.flatpack;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.flatpack.FlatpackDataFormat;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class FlatpackDataSetListIteratorTest extends CamelTestSupport {

    private static final String DIRECT_URI = "direct:flatpach-test";

    @EndpointInject("mock:each-item")
    private MockEndpoint eachItemEndpoint;

    @Test
    void sendTESTwithErrors() throws Exception {
        String header = "A,B,C";
        String record1 = "1,2,3";
        String record2 = "7,8,9";
        Object body = Stream.of(header, record1, record2).collect(Collectors.joining("\r\n"));

        eachItemEndpoint.expectedMessageCount(2);

        template.sendBody(DIRECT_URI, body);

        eachItemEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        FlatpackDataFormat format = new FlatpackDataFormat();
        format.setDelimiter(',');
        format.setTextQualifier('"');

        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_URI)
                    .unmarshal(format)
                    .log("Processing ${body}")
                    .split().body()
                        .to("mock:each-item")
                    .end();
            }
        };
    }

}
