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
 * Unit test that file consumer will include pre and postfixes
 */
public class FileConsumerIncludeNameTest extends ContextTestSupport {

    @Test
    public void testIncludePreAndPostfixes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Reports1", "Reports2", "Reports3");
        mock.expectedMessageCount(3);

        sendFiles();

        mock.assertIsSatisfied();
    }

    private void sendFiles() throws Exception {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.xml");
        template.sendBodyAndHeader(fileUri(), "Reports1", Exchange.FILE_NAME, "report1.txt");
        template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, "secret.txt");
        template.sendBodyAndHeader(fileUri(), "Reports2", Exchange.FILE_NAME, "report2.txt");
        template.sendBodyAndHeader(fileUri(), "Reports3", Exchange.FILE_NAME, "Report3.txt");
        template.sendBodyAndHeader(fileUri(), "Secret2", Exchange.FILE_NAME, "Secret2.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUri("?initialDelay=0&delay=10&include=^report.*txt$")).convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

}
