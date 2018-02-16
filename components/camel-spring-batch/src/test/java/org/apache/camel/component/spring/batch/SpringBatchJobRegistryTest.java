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

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.spring.batch.support.CamelItemProcessor;
import org.apache.camel.component.spring.batch.support.CamelItemReader;
import org.apache.camel.component.spring.batch.support.CamelItemWriter;
import org.apache.camel.component.spring.batch.support.CamelJobExecutionListener;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.support.ApplicationContextFactory;
import org.springframework.batch.core.configuration.support.GenericApplicationContextFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@RunWith(CamelSpringRunner.class)
@ContextConfiguration(classes = SpringBatchJobRegistryTest.ContextConfig.class, loader = CamelSpringDelegatingTestContextLoader.class)
public class SpringBatchJobRegistryTest extends AbstractJUnit4SpringContextTests {

    @EndpointInject(uri = "mock:output")
    MockEndpoint outputEndpoint;

    @EndpointInject(uri = "mock:jobExecutionEventsQueue")
    MockEndpoint jobExecutionEventsQueueEndpoint;

    @Autowired
    ProducerTemplate template;

    @Autowired
    ConsumerTemplate consumer;

    String[] inputMessages = new String[]{"foo", "bar", "baz", null};

    @Before
    public void setUp() throws Exception {

        for (String message : inputMessages) {
            template.sendBody("seda:inputQueue", message);
        }
    }

    @DirtiesContext
    @Test
    public void testJobRegistry() throws InterruptedException {
        outputEndpoint.expectedBodiesReceived("Echo foo", "Echo bar", "Echo baz");

        template.sendBody("direct:start", "Start batch!");

        outputEndpoint.assertIsSatisfied();
    }

    @Configuration
    @Import(value = BatchConfig.class)
    public static class ContextConfig extends SingleRouteCamelConfiguration {
        @Override
        public RouteBuilder route() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").to("spring-batch:echoJob?jobRegistry=#jobRegistry");
                    from("direct:processor").setExchangePattern(ExchangePattern.InOut).setBody(simple("Echo ${body}"));
                }
            };
        }
    }

    @EnableBatchProcessing(modular = true)
    public static class BatchConfig {

        @Bean
        public ApplicationContextFactory testJobs() {
            return new GenericApplicationContextFactory(ChildBatchConfig.class);
        }
    }

    @Configuration
    public static class ChildBatchConfig {

        @Autowired
        JobBuilderFactory jobs;

        @Autowired
        StepBuilderFactory steps;

        @Autowired
        ConsumerTemplate consumerTemplate;

        @Autowired
        ProducerTemplate producerTemplate;

        @Bean
        protected ItemReader<Object> reader() throws Exception {
            return new CamelItemReader<Object>(consumerTemplate, "seda:inputQueue");
        }

        @Bean
        protected ItemWriter<Object> writer() throws Exception {
            return new CamelItemWriter<Object>(producerTemplate, "mock:output");
        }

        @Bean
        protected ItemProcessor<Object, Object> processor() throws Exception {
            return new CamelItemProcessor<Object, Object>(producerTemplate, "direct:processor");
        }

        @Bean
        protected JobExecutionListener jobExecutionListener() throws Exception {
            return new CamelJobExecutionListener(producerTemplate, "mock:jobExecutionEventsQueue");
        }

        @Bean
        public Job echoJob() throws Exception {
            return this.jobs.get("echoJob").start(echoStep()).build();
        }

        @Bean
        protected Step echoStep() throws Exception {
            return this.steps.get("echoStep")
                    .chunk(3)
                    .reader(reader())
                    .processor(processor())
                    .writer(writer())
                    .build();
        }
    }

}
