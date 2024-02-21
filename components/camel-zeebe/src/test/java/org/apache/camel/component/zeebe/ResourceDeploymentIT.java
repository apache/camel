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

package org.apache.camel.component.zeebe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.zeebe.model.DeploymentRequest;
import org.apache.camel.component.zeebe.model.DeploymentResponse;
import org.apache.camel.component.zeebe.model.ProcessDeploymentResponse;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "zeebe.test.integration.enable", matches = "true",
                         disabledReason = "Requires locally installed test system")
public class ResourceDeploymentIT extends CamelTestSupport {

    public static final String RESOURCE_PATH = "data/";
    public static final String RESOURCE_NAME = "test1_definition.bpmn";

    public static final String INVALID_RESOURCE_NAME = "test1_definition.txt";

    protected ZeebeComponent component;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testUploadProcessDefinition() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:deployResource");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(ZeebeConstants.IS_SUCCESS, true);
        mock.expectedHeaderReceived(ZeebeConstants.RESOURCE_NAME, RESOURCE_NAME);

        DeploymentRequest deploymentRequest = new DeploymentRequest();
        deploymentRequest.setName(RESOURCE_NAME);
        deploymentRequest
                .setContent(this.getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH + RESOURCE_NAME).readAllBytes());

        template.sendBody("direct:deployResource", deploymentRequest);
        MockEndpoint.assertIsSatisfied(context);
        if (!mock.getExchanges().isEmpty()) {
            Exchange exchange = mock.getExchanges().get(0);
            Object body = exchange.getMessage().getBody();
            assertTrue(body instanceof ProcessDeploymentResponse);
            assertTrue(((ProcessDeploymentResponse) body).isSuccess());
            assertTrue(exchange.getMessage().getHeaders().containsKey(ZeebeConstants.PROCESS_DEFINITION_KEY));
            assertTrue(exchange.getMessage().getHeaders().containsKey(ZeebeConstants.BPMN_PROCESS_ID));
            assertTrue(exchange.getMessage().getHeaders().containsKey(ZeebeConstants.VERSION));
        }

        // Deploy with resource name in header and resource as byte[] in body
        template.sendBodyAndHeader("direct:deployResource",
                this.getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH + RESOURCE_NAME).readAllBytes(),
                ZeebeConstants.RESOURCE_NAME, RESOURCE_NAME);
        MockEndpoint.assertIsSatisfied(context);
        if (!mock.getExchanges().isEmpty()) {
            Exchange exchange = mock.getExchanges().get(0);
            Object body = exchange.getMessage().getBody();
            assertTrue(body instanceof ProcessDeploymentResponse);
            assertTrue(((ProcessDeploymentResponse) body).isSuccess());
        }

        // Deploy with resource name in header and resource as String in body
        template.sendBodyAndHeader("direct:deployResource",
                new String(this.getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH + RESOURCE_NAME).readAllBytes()),
                ZeebeConstants.RESOURCE_NAME, RESOURCE_NAME);
        MockEndpoint.assertIsSatisfied(context);
        if (!mock.getExchanges().isEmpty()) {
            Exchange exchange = mock.getExchanges().get(0);
            Object body = exchange.getMessage().getBody();
            assertTrue(body instanceof ProcessDeploymentResponse);
            assertTrue(((ProcessDeploymentResponse) body).isSuccess());
        }
    }

    @Test
    void testUploadProcessDefinitionJSON() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:deployResource_JSON");
        mock.expectedMinimumMessageCount(1);

        DeploymentRequest deploymentRequest = new DeploymentRequest();
        deploymentRequest.setName(RESOURCE_NAME);
        deploymentRequest
                .setContent(this.getClass().getClassLoader().getResourceAsStream("data/test1_definition.bpmn").readAllBytes());

        template.sendBody("direct:deployResource_JSON", objectMapper.writeValueAsString(deploymentRequest));
        MockEndpoint.assertIsSatisfied(context);
        if (!mock.getExchanges().isEmpty()) {
            Exchange exchange = mock.getExchanges().get(0);
            String body = exchange.getMessage().getBody(String.class);
            ProcessDeploymentResponse response = objectMapper.readValue(body, ProcessDeploymentResponse.class);
            assertTrue(response.isSuccess());
        }
    }

    @Test
    void testInvalidUploadProcessDefinition() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:deployResource");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(ZeebeConstants.IS_SUCCESS, false);

        DeploymentRequest deploymentRequest = new DeploymentRequest();
        deploymentRequest.setName(INVALID_RESOURCE_NAME);
        deploymentRequest
                .setContent(this.getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH + RESOURCE_NAME).readAllBytes());

        template.sendBody("direct:deployResource", deploymentRequest);
        MockEndpoint.assertIsSatisfied(context);
        if (!mock.getExchanges().isEmpty()) {
            Exchange exchange = mock.getExchanges().get(0);
            Object body = exchange.getMessage().getBody();
            assertTrue(body instanceof DeploymentResponse);
            assertFalse(((DeploymentResponse) body).isSuccess());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        createComponent();

        return new RouteBuilder() {
            public void configure() {
                from("direct:deployResource")
                        .to("zeebe://deployResource")
                        .to("mock:deployResource");

                from("direct:deployResource_JSON")
                        .to("zeebe://deployResource?formatJSON=true")
                        .to("mock:deployResource_JSON");
            }
        };
    }

    protected void createComponent() throws Exception {
        component = new ZeebeComponent();

        component.setGatewayHost(ZeebeConstants.DEFAULT_GATEWAY_HOST);
        component.setGatewayPort(ZeebeConstants.DEFAULT_GATEWAY_PORT);

        context().addComponent("zeebe", component);
    }
}
