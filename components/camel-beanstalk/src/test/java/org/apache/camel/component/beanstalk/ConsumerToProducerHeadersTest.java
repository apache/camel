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
package org.apache.camel.component.beanstalk;

import java.util.HashMap;
import java.util.Map;

import com.surftools.BeanstalkClient.Job;
import org.apache.camel.EndpointInject;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisabledOnOs(OS.WINDOWS)
public class ConsumerToProducerHeadersTest extends BeanstalkMockTestSupport {

    @EndpointInject("beanstalk:tube=A")
    protected BeanstalkEndpoint endpoint;

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    private Processor a;
    private Processor b;

    @Test
    void testBeanstalkConsumerToProducer() throws Exception {
        final long jobId = 111;
        String testMessage = "hello, world";
        final byte[] payload = Helper.stringToBytes(testMessage);
        final Job jobMock = mock(Job.class);
        // stats that may be set in the consumer:
        // mock stats : "tube", "state", "age", "time-left", "timeouts", "releases", "buries", "kicks"
        Map<String, String> stats = new HashMap<>();
        stats.put("tube", "A");
        stats.put("state", "Test");
        stats.put("age", "0");
        stats.put("time-left", "0");
        stats.put("timeouts", "0");
        stats.put("releases", "0");
        stats.put("buries", "0");
        stats.put("kicks", "0");

        when(jobMock.getJobId()).thenReturn(jobId);
        when(jobMock.getData()).thenReturn(payload);
        when(client.reserve(anyInt()))
                .thenReturn(jobMock)
                .thenReturn(null);
        when(client.statsJob(anyLong())).thenReturn(stats);
        
        when(client.put(BeanstalkComponent.DEFAULT_PRIORITY, 
                        BeanstalkComponent.DEFAULT_DELAY, 
                        BeanstalkComponent.DEFAULT_TIME_TO_RUN, 
                        payload)).thenReturn(jobId);

        MockEndpoint result = getMockEndpoint("mock:result");

        result.expectedMinimumMessageCount(1);
        result.expectedBodiesReceived(testMessage);
        result.expectedHeaderReceived(Headers.JOB_ID, jobId);
        result.message(0).header(Headers.JOB_ID).isEqualTo(jobId);

        context.getRouteController().startRoute("foo");

        result.assertIsSatisfied();

        verify(client, atLeastOnce()).reserve(anyInt());
        verify(client, atLeastOnce()).statsJob(anyLong());
     
        assertEquals(((TestExchangeCopyProcessor)a).getExchangeCopy().getIn().getHeaders(),
                     ((TestExchangeCopyProcessor)b).getExchangeCopy().getIn().getHeaders());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                a = new TestExchangeCopyProcessor();
                b = new TestExchangeCopyProcessor();
                
                from("beanstalk:tube=A").routeId("foo")
                    .process(a)
                    .to("beanstalk:tube=B")
                    .process(b)
                    .to("mock:result");
            }
        };
    }
}
