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

/**
 * Unit test for the filter file option
 */
public class FileConsumerFilterFileTest extends ContextTestSupport {

    public static final String FILE_URL_1 = "?initialDelay=0&delay=10&"
                                            + "filterFile=${bodyAs(String)} contains 'World'";
    public static final String FILE_URL_2 = "?initialDelay=0&delay=10&"
                                            + "filterFile=${file:modified} < ${date:now-2s}";

    @Test
    public void testFilterFiles() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader(fileUri(), "This is a file to be filtered", Exchange.FILE_NAME, "skipme.txt");

        mock.setResultWaitTime(100);
        mock.assertIsSatisfied();
    }

    @Test
    public void testFilterFilesWithARegularFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "This is a file to be filtered", Exchange.FILE_NAME, "skipme2.txt");
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();
    }

    @Test
    public void testFilterFilesWithDate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result2");
        mock.expectedBodiesReceived("Something else");

        template.sendBodyAndHeader(fileUri(), "Something else", Exchange.FILE_NAME, "hello2.txt");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUri(FILE_URL_1)).convertBodyTo(String.class).to("mock:result");
                from(fileUri(FILE_URL_2)).convertBodyTo(String.class).to("mock:result2");
            }
        };
    }

}
