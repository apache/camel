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
package org.apache.camel.component.spring.batch;

import java.util.Date;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.lang.reflect.FieldUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;


import static org.mockito.BDDMockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SpringBatchEndpointTest extends CamelTestSupport {

    // Fixtures

    @Mock
    JobLauncher jobLauncher;

    @Mock
    JobLauncher alternativeJobLauncher;

    @Mock
    Job job;

    // Camel fixtures

    @EndpointInject(uri = "mock:test")
    MockEndpoint mockEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("spring-batch:mockJob").to("mock:test");
            }
        };
    }

    @Override
    public JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("jobLauncher", jobLauncher);
        registry.bind("alternativeJobLauncher", alternativeJobLauncher);
        registry.bind("mockJob", job);
        return registry;
    }

    // Tests

    @Test
    public void shouldInjectJobToEndpoint() throws IllegalAccessException {
        SpringBatchEndpoint batchEndpoint = getMandatoryEndpoint("spring-batch:mockJob", SpringBatchEndpoint.class);
        Job batchEndpointJob = (Job) FieldUtils.readField(batchEndpoint, "job", true);
        assertSame(job, batchEndpointJob);
    }

    @Test
    public void shouldRunJob() throws Exception {
        // When
        sendBody("direct:start", "Start the job, please.");

        // Then
        verify(jobLauncher).run(eq(job), any(JobParameters.class));
    }

    @Test
    public void shouldReturnJobExecution() throws Exception {
        // Given
        JobExecution jobExecution = mock(JobExecution.class);
        when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(jobExecution);

        // When
        sendBody("direct:start", "Start the job, please.");

        // Then
        mockEndpoint.expectedBodiesReceived(jobExecution);
    }

    @Test(expected = FailedToCreateRouteException.class)
    public void shouldThrowExceptionIfUsedAsConsumer() throws Exception {
        // When
        context().addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("spring-batch:mockJob").to("direct:emptyEndpoint");
            }
        });
    }

    @Test
    public void shouldConvertHeadersToJobParams() throws Exception {
        // Given
        String headerKey = "headerKey";
        String headerValue = "headerValue";

        // When
        template.sendBodyAndHeader("direct:start", "Start the job, please.", headerKey, headerValue);

        // Then
        ArgumentCaptor<JobParameters> jobParameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(any(Job.class), jobParameters.capture());
        String parameter = jobParameters.getValue().getString(headerKey);
        assertEquals(parameter, headerValue);
    }
    
    @Test 
    public void setNullValueToJobParams() throws Exception {
     // Given
        String headerKey = "headerKey";
        Date headerValue = null;

        // When
        template.sendBodyAndHeader("direct:start", "Start the job, please.", headerKey, headerValue);

        // Then
        ArgumentCaptor<JobParameters> jobParameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(any(Job.class), jobParameters.capture());
        Date parameter = jobParameters.getValue().getDate(headerKey);
        assertEquals(parameter, headerValue);
    }

    @Test
    public void shouldConvertDateHeadersToJobParams() throws Exception {
        // Given
        String headerKey = "headerKey";
        Date headerValue = new Date();

        // When
        template.sendBodyAndHeader("direct:start", "Start the job, please.", headerKey, headerValue);

        // Then
        ArgumentCaptor<JobParameters> jobParameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(any(Job.class), jobParameters.capture());
        Date parameter = jobParameters.getValue().getDate(headerKey);
        assertEquals(parameter, headerValue);
    }

    @Test
    public void shouldConvertLongHeadersToJobParams() throws Exception {
        // Given
        String headerKey = "headerKey";
        Long headerValue = 1L;

        // When
        template.sendBodyAndHeader("direct:start", "Start the job, please.", headerKey, headerValue);

        // Then
        ArgumentCaptor<JobParameters> jobParameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(any(Job.class), jobParameters.capture());
        Long parameter = jobParameters.getValue().getLong(headerKey);
        assertEquals(parameter, headerValue);
    }

    @Test
    public void shouldConvertDoubleHeadersToJobParams() throws Exception {
        // Given
        String headerKey = "headerKey";
        Double headerValue = 1.0;

        // When
        template.sendBodyAndHeader("direct:start", "Start the job, please.", headerKey, headerValue);

        // Then
        ArgumentCaptor<JobParameters> jobParameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(any(Job.class), jobParameters.capture());
        Double parameter = jobParameters.getValue().getDouble(headerKey);
        assertEquals(parameter, headerValue);
    }

    @Test
    public void shouldInjectJobLauncherByReferenceName() throws Exception {
        // Given
        context().addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:launcherRefTest").to("spring-batch:mockJob?jobLauncher=#alternativeJobLauncher");
            }
        });

        // When
        template.sendBody("direct:launcherRefTest", "Start the job, please.");

        // Then
        SpringBatchEndpoint batchEndpoint = context().getEndpoint("spring-batch:mockJob?jobLauncher=#alternativeJobLauncher", SpringBatchEndpoint.class);
        JobLauncher batchEndpointJobLauncher = (JobLauncher) FieldUtils.readField(batchEndpoint, "jobLauncher", true);
        assertSame(alternativeJobLauncher, batchEndpointJobLauncher);
    }

    @Test(expected = FailedToCreateRouteException.class)
    public void shouldFailWhenThereIsNoJobLauncher() throws Exception {
        // Given
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("mockJob", job);
        CamelContext camelContext = new DefaultCamelContext(registry);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("spring-batch:mockJob");
            }
        });

        // When
        camelContext.start();
    }

    @Test(expected = FailedToCreateRouteException.class)
    public void shouldFailWhenThereIsMoreThanOneJobLauncher() throws Exception {
        // Given
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("mockJob", job);
        registry.put("launcher1", jobLauncher);
        registry.put("launcher2", jobLauncher);
        CamelContext camelContext = new DefaultCamelContext(registry);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("spring-batch:mockJob");
            }
        });

        // When
        camelContext.start();
    }

    @Test
    public void shouldResolveAnyJobLauncher() throws Exception {
        // Given
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("mockJob", job);
        registry.put("someRandomName", jobLauncher);
        CamelContext camelContext = new DefaultCamelContext(registry);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("spring-batch:mockJob");
            }
        });

        // When
        camelContext.start();

        // Then
        SpringBatchEndpoint batchEndpoint = camelContext.getEndpoint("spring-batch:mockJob", SpringBatchEndpoint.class);
        JobLauncher batchEndpointJobLauncher = (JobLauncher) FieldUtils.readField(batchEndpoint, "jobLauncher", true);
        assertSame(jobLauncher, batchEndpointJobLauncher);
    }

    @Test
    public void shouldUseJobLauncherFromComponent() throws Exception {
        // Given
        SpringBatchComponent batchComponent = new SpringBatchComponent();
        batchComponent.setJobLauncher(alternativeJobLauncher);
        context.addComponent("customBatchComponent", batchComponent);

        // When
        context().addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:startCustom").to("customBatchComponent:mockJob");
            }
        });

        // Then
        SpringBatchEndpoint batchEndpoint = context().getEndpoint("customBatchComponent:mockJob", SpringBatchEndpoint.class);
        JobLauncher batchEndpointJobLauncher = (JobLauncher) FieldUtils.readField(batchEndpoint, "jobLauncher", true);
        assertSame(alternativeJobLauncher, batchEndpointJobLauncher);
    }

}
