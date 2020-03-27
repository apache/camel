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
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.beanstalk.Headers;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TouchProducerIntegrationTest extends BeanstalkCamelTestSupport {
    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce("direct:start")
    protected ProducerTemplate direct;

    @Disabled("requires reserve - touch sequence")
    @Test
    void testBury() throws InterruptedException, IOException {
        long jobId = writer.put(0, 0, 5, new byte[0]);
        assertTrue(jobId > 0, "Valid Job Id");

        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.allMessages().header(Headers.JOB_ID).isNotNull();
        resultEndpoint.allMessages().header(Headers.RESULT).isEqualTo(true);
        direct.sendBodyAndHeader(null, Headers.JOB_ID, jobId);

        assertMockEndpointsSatisfied();

        final Long messageJobId = resultEndpoint.getReceivedExchanges().get(0).getIn().getHeader(Headers.JOB_ID, Long.class);
        assertNotNull(messageJobId, "Job ID in message");
        assertEquals(jobId, messageJobId.longValue(), "Message Job ID equals");

        final Job job = reader.reserve(0);
        assertNull(job, "Beanstalk client has no message");

        final Job buried = reader.peekBuried();
        assertNotNull(buried, "Job in buried");
        assertEquals(jobId, buried.getJobId(), "Buried job id");
    }

    @Test
    void testNoJobId() {
        assertThrows(CamelExecutionException.class, () -> {
            direct.sendBody(new byte[0]);
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("beanstalk:" + tubeName + "?command=touch").to("mock:result");
            }
        };
    }
}
