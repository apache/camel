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
package org.apache.camel.dataformat.bindy.fixed.link;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.csv.BindyCsvDataFormat;
import org.apache.camel.dataformat.bindy.model.fixed.link.MyModel;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.RepeatedTest;

import static java.util.concurrent.TimeUnit.SECONDS;

/** Test for CAMEL-19833 **/
public class BindyRaceConditionLinkTest extends CamelTestSupport {

    private static final String SOURCE_CSV_FILE_PATH
            = "org/apache/camel/dataformat/bindy/fixed/link/bindyRaceConditionLinkTest.csv";

    private static final int EXPECTED_SUCCESSFUL_MESSAGE_COUNT = 1500;
    private static final int EXPECTED_FAILED_MESSAGE_COUNT = 0;
    private static final long WAIT_TIMEOUT = 5;

    @EndpointInject("mock:fail")
    private MockEndpoint fail;

    @EndpointInject("mock:end")
    private MockEndpoint end;

    @EndpointInject("direct:bindy-link-test")
    private Endpoint begin;

    @RepeatedTest(3)
    public void raceConditionTest() throws Exception {
        Path filePath = Path.of(ClassLoader.getSystemResource(SOURCE_CSV_FILE_PATH).toURI());
        String csv = Files.readString(filePath);
        template.sendBody(begin, csv);
        fail.expectedMessageCount(EXPECTED_FAILED_MESSAGE_COUNT);
        end.expectedMessageCount(EXPECTED_SUCCESSFUL_MESSAGE_COUNT);
        MockEndpoint.assertIsSatisfied(context, WAIT_TIMEOUT, SECONDS);
        end.reset();
        fail.reset();
    }

    @Override
    public RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                BindyCsvDataFormat bindy = new BindyCsvDataFormat(MyModel.class);

                onException(UnsupportedOperationException.class)
                        .handled(true)
                        .to(fail);

                from(begin)
                        .routeId("bindy-link-test")
                        .convertBodyTo(String.class)
                        .split(body().tokenize("\n"))
                        .parallelProcessing(true)
                        .unmarshal(bindy)
                        .to(end);
            }
        };
    }
}
