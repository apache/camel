/**
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

import com.surftools.BeanstalkClient.BeanstalkException;
import com.surftools.BeanstalkClient.Job;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AwaitingConsumerTest extends BeanstalkMockTestSupport {

    @EndpointInject(uri = "beanstalk:tube")
    protected BeanstalkEndpoint endpoint;

    private String testMessage = "hello, world";

    @Test
    public void testReceive() throws Exception {
        if (!canTest()) {
            return;
        }

        final Job jobMock = mock(Job.class);
        final long jobId = 111;
        final byte[] payload = Helper.stringToBytes(testMessage);

        when(jobMock.getJobId()).thenReturn(jobId);
        when(jobMock.getData()).thenReturn(payload);
        when(client.reserve(anyInt()))
                .thenReturn(jobMock)
                .thenReturn(null);

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.expectedBodiesReceived(testMessage);
        result.expectedHeaderReceived(Headers.JOB_ID, jobId);
        result.message(0).header(Headers.JOB_ID).isEqualTo(jobId);
        result.assertIsSatisfied(100);

        verify(client, atLeast(1)).reserve(0);
        verify(client, atLeast(1)).delete(jobId);
    }

    @Test
    public void testBeanstalkException() throws Exception {
        if (!canTest()) {
            return;
        }

        final Job jobMock = mock(Job.class);
        final long jobId = 111;
        final byte[] payload = Helper.stringToBytes(testMessage);

        when(jobMock.getJobId()).thenReturn(jobId);
        when(jobMock.getData()).thenReturn(payload);
        when(client.reserve(anyInt()))
                .thenThrow(new BeanstalkException("test"))
                .thenReturn(jobMock);

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.expectedBodiesReceived(testMessage);
        result.expectedHeaderReceived(Headers.JOB_ID, jobId);
        result.message(0).header(Headers.JOB_ID).isEqualTo(jobId);
        result.assertIsSatisfied(100);

        verify(client, atLeast(1)).reserve(anyInt());
        verify(client, times(1)).close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("beanstalk:tube").to("mock:result");
            }
        };
    }
}
