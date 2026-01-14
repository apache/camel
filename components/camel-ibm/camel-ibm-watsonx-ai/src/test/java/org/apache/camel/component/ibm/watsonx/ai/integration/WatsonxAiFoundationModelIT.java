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
package org.apache.camel.component.ibm.watsonx.ai.integration;

import java.util.List;

import com.ibm.watsonx.ai.foundationmodel.FoundationModel;
import com.ibm.watsonx.ai.foundationmodel.FoundationModelTask;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for watsonx.ai foundation model discovery operations.
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.apiKey", matches = ".+",
                                 disabledReason = "IBM watsonx.ai API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.projectId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Project ID not provided")
})
public class WatsonxAiFoundationModelIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiFoundationModelIT.class);

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListModels() throws Exception {
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:listModels", null);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof List, "Response should be a list");

        List<FoundationModel> models = (List<FoundationModel>) body;
        assertFalse(models.isEmpty(), "Models list should not be empty");

        LOG.info("Found {} foundation models", models.size());

        // Log some model names
        models.stream().forEach(model -> LOG.info("Model: {} - {}", model.modelId(), model.label()));

        // Verify header
        List<FoundationModel> modelsHeader = exchange.getIn().getHeader(WatsonxAiConstants.FOUNDATION_MODELS, List.class);
        assertNotNull(modelsHeader, "Models header should be set");
        assertEquals(models.size(), modelsHeader.size(), "Header and body should have same model count");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListTasks() throws Exception {
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:listTasks", null);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof List, "Response should be a list");

        List<FoundationModelTask> tasks = (List<FoundationModelTask>) body;
        assertFalse(tasks.isEmpty(), "Tasks list should not be empty");

        LOG.info("Found {} foundation model tasks", tasks.size());

        // Log all tasks
        tasks.forEach(task -> LOG.info("Task: {} - {}", task.taskId(), task.label()));

        // Verify header
        List<FoundationModelTask> tasksHeader
                = exchange.getIn().getHeader(WatsonxAiConstants.FOUNDATION_MODEL_TASKS, List.class);
        assertNotNull(tasksHeader, "Tasks header should be set");
        assertEquals(tasks.size(), tasksHeader.size(), "Header and body should have same task count");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:listModels")
                        .to(buildEndpointUri("listModels", null))
                        .to("mock:result");

                from("direct:listTasks")
                        .to(buildEndpointUri("listTasks", null))
                        .to("mock:result");
            }
        };
    }
}
