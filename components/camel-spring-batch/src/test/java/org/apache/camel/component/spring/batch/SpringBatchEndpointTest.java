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

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;

import static org.apache.camel.test.junit5.TestSupport.header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
public class SpringBatchEndpointTest extends CamelTestSupport {

    // Fixtures
    @Mock
    JobLauncher jobLauncher;

    @Mock
    JobLauncher alternativeJobLauncher;

    @Mock
    JobRegistry jobRegistry;

    @Mock
    Job job;

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
                from("direct:start").to("spring-batch:mockJob").to("mock:test");
                from("direct:dynamic").to("spring-batch:fake?jobFromHeader=true").errorHandler(deadLetterChannel("mock:error"))
                        .to("mock:test");
                from("direct:dynamicWithJobRegistry").to("spring-batch:fake?jobFromHeader=true&jobRegistry=#jobRegistry")
                        .errorHandler(deadLetterChannel("mock:error")).to("mock:test");
            }
        };
    }

    @Override
    public Registry createCamelRegistry() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("jobLauncher", jobLauncher);
        registry.bind("alternativeJobLauncher", alternativeJobLauncher);
        registry.bind("mockJob", job);
        registry.bind("dynamicMockjob", dynamicMockjob);
        registry.bind("jobRegistry", jobRegistry);
        return registry;
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

        //dynamic job should fail as header is present but the job does not exists
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

    @Test
    public void shouldThrowExceptionIfUsedAsConsumer() {
        RouteBuilder rb = new RouteBuilder() {
            @Override
            public void configure() {
                from("spring-batch:mockJob").to("direct:emptyEndpoint");
            }
        };
        final CamelContext context = context();

        // When
        assertThrows(FailedToStartRouteException.class,
                () -> context.addRoutes(rb));
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
        SpringBatchEndpoint batchEndpoint
                = context().getEndpoint("spring-batch:mockJob?jobLauncher=#alternativeJobLauncher", SpringBatchEndpoint.class);
        JobLauncher batchEndpointJobLauncher = (JobLauncher) FieldUtils.readField(batchEndpoint, "jobLauncher", true);
        assertSame(alternativeJobLauncher, batchEndpointJobLauncher);
    }

    @Test
    public void shouldFailWhenThereIsNoJobLauncher() throws Exception {
        // Given
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("mockJob", job);
        CamelContext camelContext = new DefaultCamelContext(registry);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("spring-batch:mockJob");
            }
        });

        // When
        assertThrows(FailedToCreateRouteException.class,
                () -> camelContext.start());
    }

    @Test
    public void shouldFailWhenThereIsMoreThanOneJobLauncher() throws Exception {
        // Given
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("mockJob", job);
        registry.bind("launcher1", jobLauncher);
        registry.bind("launcher2", jobLauncher);
        CamelContext camelContext = new DefaultCamelContext(registry);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("spring-batch:mockJob");
            }
        });

        // When
        assertThrows(FailedToCreateRouteException.class,
                () -> camelContext.start());
    }

    @Test
    public void shouldResolveAnyJobLauncher() throws Exception {
        // Given
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("mockJob", job);
        registry.bind("someRandomName", jobLauncher);
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

    @Test
    public void shouldInjectJobRegistryByReferenceName() throws Exception {
        // Given
        Job mockJob = mock(Job.class);
        when(jobRegistry.getJob(eq("mockJob"))).thenReturn(mockJob);

        context().addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:jobRegistryRefTest").to("spring-batch:mockJob?jobRegistry=#jobRegistry");
            }
        });

        // When
        template.sendBody("direct:jobRegistryRefTest", "Start the job, please.");

        // Then
        SpringBatchEndpoint batchEndpoint
                = context().getEndpoint("spring-batch:mockJob?jobRegistry=#jobRegistry", SpringBatchEndpoint.class);
        JobRegistry batchEndpointJobRegistry = (JobRegistry) FieldUtils.readField(batchEndpoint, "jobRegistry", true);
        assertSame(jobRegistry, batchEndpointJobRegistry);
    }

    @Test
    public void shouldUseJobRegistryFromComponent() throws Exception {
        // Given
        SpringBatchComponent batchComponent = new SpringBatchComponent();
        batchComponent.setJobRegistry(jobRegistry);
        batchComponent.setJobLauncher(jobLauncher);
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
        JobRegistry batchEndpointJobRegistry = (JobRegistry) FieldUtils.readField(batchEndpoint, "jobRegistry", true);
        assertSame(jobRegistry, batchEndpointJobRegistry);
    }

    @Test
    public void shouldGetJobFromJobRegistry() throws Exception {
        // Given
        Job mockJobFromJobRegistry = mock(Job.class);
        when(jobRegistry.getJob(eq("mockJobFromJobRegistry"))).thenReturn(mockJobFromJobRegistry);

        // When
        context().addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:jobRegistryTest").to("spring-batch:mockJobFromJobRegistry?jobRegistry=#jobRegistry");
            }
        });

        // Then
        SpringBatchEndpoint batchEndpoint = context()
                .getEndpoint("spring-batch:mockJobFromJobRegistry?jobRegistry=#jobRegistry", SpringBatchEndpoint.class);
        Job batchEndpointJob = (Job) FieldUtils.readField(batchEndpoint, "job", true);
        assertSame(mockJobFromJobRegistry, batchEndpointJob);
    }
}
