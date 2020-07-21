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

import com.surftools.BeanstalkClient.Job;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.beanstalk.Headers;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PutProducerIntegrationTest extends BeanstalkCamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce("direct:start")
    protected ProducerTemplate direct;

    private final String testMessage = "Hello, world!";

    @Test
    void testPut() throws InterruptedException, IOException {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.allMessages().header(Headers.JOB_ID).isNotNull();
        direct.sendBody(testMessage);

        resultEndpoint.assertIsSatisfied();

        final Long jobId = resultEndpoint.getReceivedExchanges().get(0).getIn().getHeader(Headers.JOB_ID, Long.class);
        assertNotNull(jobId, "Job ID in 'In' message");

        final Job job = reader.reserve(5);
        assertNotNull(job, "Beanstalk client got message");
        assertEquals(testMessage, new String(job.getData()), "Job body from the server");
        assertEquals(jobId.longValue(), job.getJobId(), "Job ID from the server");
        reader.delete(jobId);
    }

    @Test
    void testOut() throws InterruptedException, IOException {
        final Endpoint endpoint = context.getEndpoint("beanstalk:" + tubeName);
        final Exchange exchange = template.send(endpoint, ExchangePattern.InOut, exchange1 -> exchange1.getIn().setBody(testMessage));

        final Message out = exchange.getMessage();
        assertNotNull(out, "Out message");

        final Long jobId = out.getHeader(Headers.JOB_ID, Long.class);
        assertNotNull(jobId, "Job ID in 'Out' message");

        final Job job = reader.reserve(5);
        assertNotNull(job, "Beanstalk client got message");
        assertEquals(testMessage, new String(job.getData()), "Job body from the server");
        assertEquals(jobId.longValue(), job.getJobId(), "Job ID from the server");
        reader.delete(jobId);
    }

    @Test
    void testDelay() throws InterruptedException, IOException {
        final byte[] testBytes = new byte[0];

        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.allMessages().header(Headers.JOB_ID).isNotNull();
        resultEndpoint.expectedBodiesReceived(testBytes);
        direct.sendBodyAndHeader(testBytes, Headers.DELAY, 10);

        resultEndpoint.assertIsSatisfied();

        final Long jobId = resultEndpoint.getReceivedExchanges().get(0).getIn().getHeader(Headers.JOB_ID, Long.class);
        assertNotNull(jobId, "Job ID in message");

        final Job job = reader.reserve(0);
        assertNull(job, "Beanstalk client has no message");
        reader.delete(jobId);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("beanstalk:" + tubeName + "?jobPriority=1000&jobTimeToRun=5").to("mock:result");
            }
        };
    }
}
