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
package org.apache.camel.component.ironmq.integrationtest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ironmq.IronMQConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

@Disabled("Integration test that requires ironmq account.")
public class FileCopyManualTest extends CamelTestSupport {
    // replace with your proejctid
    private String projectId = "myIronMQproject";
    // replace with your token
    private String token = "myIronMQToken";

    private final String ironMQEndpoint = "ironmq:testqueue?projectId=" + projectId + "&token=" + token
                                          + "&ironMQCloud=https://mq-aws-eu-west-1-1.iron.io&preserveHeaders=true";

    @BeforeEach
    public void clean() {
        template.sendBodyAndHeader(ironMQEndpoint, "fo", IronMQConstants.OPERATION, IronMQConstants.CLEARQUEUE);
        deleteDirectory("target/out");
    }

    @Test
    public void testCopyFileOverIronMQ() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context);
        assertFileExists("target/out/test.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                //copies test.txt from test/data to ironmq
                from("file:src/test/data?noop=true").convertBodyTo(String.class).log("sending : ${body}").to(ironMQEndpoint);
                //Receives test.txt from ironmq and writes it to target/out
                from(ironMQEndpoint).log("got message : ${body}").to("file:target/out").to("mock:result");
            }
        };
    }
}
