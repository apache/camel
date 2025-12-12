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
package org.apache.camel.component.spring.batch;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;

import static org.apache.camel.test.junit6.TestSupport.header;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
public class SpringBatchEndpointDynamicTest extends CamelTestSupport {

    // Fixtures
    @Mock
    JobLauncher jobLauncher;

    @Mock
    JobRegistry jobRegistry;

    @Mock
    Job dynamicMockjob;

    // Camel fixtures
    @EndpointInject("mock:test")
    MockEndpoint mockEndpoint;

    @EndpointInject("mock:error")
    MockEndpoint errorEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:dynamic").to("spring-batch:fake?jobFromHeader=true").errorHandler(deadLetterChannel("mock:error"))
                        .to("mock:test");
                from("direct:dynamicWithJobRegistry").to("spring-batch:fake?jobFromHeader=true&jobRegistry=#jobRegistry")
                        .errorHandler(deadLetterChannel("mock:error")).to("mock:test");
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("jobLauncher", jobLauncher);
        registry.bind("dynamicMockjob", dynamicMockjob);
        registry.bind("jobRegistry", jobRegistry);
    }

    // Tests
    @Test
    public void dynamicJobFailsIfHeaderNotPressent() throws Exception {

        mockEndpoint.expectedMessageCount(0);
        errorEndpoint.expectedMessageCount(1);

        //dynamic job should fail as header is not present and the job is dynamic
        sendBody("direct:dyanmic?block=false", "Start the job, please.");
        mockEndpoint.assertIsSatisfied();
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void dynamicJobFailsIfHeaderWithInvalidJobName() throws Exception {

        mockEndpoint.expectedMessageCount(0);
        errorEndpoint.expectedMessageCount(1);

        //dynamic job should fail as header is present but the job does not exist
        header(SpringBatchConstants.JOB_NAME).append("thisJobDoesNotExsistAtAll" + Date.from(Instant.now()));
        sendBody("direct:dyanmic?block=false", "Start the job, please.");

        mockEndpoint.assertIsSatisfied();
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void dynamicJobWorksIfHeaderPressentWithValidJob() throws Exception {

        mockEndpoint.expectedMessageCount(1);
        errorEndpoint.expectedMessageCount(0);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(SpringBatchConstants.JOB_NAME, "dynamicMockjob");

        sendBody("direct:dynamic?block=false", "Start the job, please.", headers);

        mockEndpoint.assertIsSatisfied();
        errorEndpoint.assertIsSatisfied();
    }

    @Test
    public void dynamicJobWorksIfHeaderPresentWithValidJobLocatedInJobRegistry() throws Exception {
        mockEndpoint.expectedMessageCount(1);
        errorEndpoint.expectedMessageCount(0);

        Job mockJob = mock(Job.class);
        when(jobRegistry.getJob(eq("dyanmicMockJobFromJobRegistry"))).thenReturn(mockJob);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(SpringBatchConstants.JOB_NAME, "dyanmicMockJobFromJobRegistry");
        headers.put("jobRegistry", "#jobRegistry");

        sendBody("direct:dynamicWithJobRegistry", "Start the job, please.", headers);

        mockEndpoint.assertIsSatisfied();
        errorEndpoint.assertIsSatisfied();
    }

}
