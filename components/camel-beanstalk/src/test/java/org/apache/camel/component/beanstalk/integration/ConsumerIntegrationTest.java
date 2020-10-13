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
package org.apache.camel.component.beanstalk.integration;

import java.io.IOException;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.beanstalk.Headers;
import org.apache.camel.component.beanstalk.Helper;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class ConsumerIntegrationTest extends BeanstalkCamelTestSupport {
    final String testMessage = "Hello, world!";

    @EndpointInject("mock:result")
    MockEndpoint result;

    @Test
    void testReceive() throws IOException, InterruptedException {
        long prio = 0;
        int ttr = 10;
        final long jobId = writer.put(prio, 0, ttr, Helper.stringToBytes(testMessage));

        result.expectedMessageCount(1);
        result.expectedHeaderReceived(Headers.JOB_ID, jobId);
        result.message(0).header(Exchange.CREATED_TIMESTAMP).isNotNull();
        result.message(0).header(Headers.JOB_ID).isEqualTo(jobId);
        result.message(0).header(Headers.PRIORITY).isEqualTo(prio);
        result.message(0).header(Headers.TUBE).isEqualTo(tubeName);
        result.message(0).header(Headers.STATE).isEqualTo("reserved");
        result.message(0).header(Headers.AGE).isGreaterThan(0);
        result.message(0).header(Headers.TIME_LEFT).isGreaterThan(0);
        result.message(0).header(Headers.TIMEOUTS).isNotNull();
        result.message(0).header(Headers.RELEASES).isNotNull();
        result.message(0).header(Headers.BURIES).isNotNull();
        result.message(0).header(Headers.KICKS).isNotNull();
        result.message(0).body().isEqualTo(testMessage);
        result.assertIsSatisfied(500);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("beanstalk:" + tubeName).to("mock:result");
            }
        };
    }
}
