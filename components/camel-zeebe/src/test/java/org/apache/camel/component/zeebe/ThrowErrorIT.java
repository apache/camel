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
import org.apache.camel.component.zeebe.model.JobRequest;
import org.apache.camel.component.zeebe.model.JobResponse;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "zeebe.test.integration.enable", matches = "true",
                         disabledReason = "Requires locally installed test system")
public class ThrowErrorIT extends CamelTestSupport {

    protected ZeebeComponent component;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testThrowError() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:throwError");
        mock.expectedMinimumMessageCount(1);

        JobRequest jobRequest = new JobRequest();
        jobRequest.setJobKey(11L);
        jobRequest.setErrorCode("TestError");
        jobRequest.setErrorMessage("TestMessage");

        template.sendBody("direct:throwError", jobRequest);
        MockEndpoint.assertIsSatisfied(context);
        if (!mock.getExchanges().isEmpty()) {
            Exchange exchange = mock.getExchanges().get(0);
            Object body = exchange.getMessage().getBody();
            assertTrue(body instanceof JobResponse);
            assertFalse(((JobResponse) body).isSuccess()); // The job does not exist in Zeebe
        }
    }

    @Test
    void testThrowErrorJSON() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:throwError_JSON");
        mock.expectedMinimumMessageCount(1);

        JobRequest jobRequest = new JobRequest();
        jobRequest.setJobKey(11L);
        jobRequest.setErrorCode("TestError");
        jobRequest.setErrorMessage("TestMessage");

        template.sendBody("direct:throwError_JSON", objectMapper.writeValueAsString(jobRequest));
        MockEndpoint.assertIsSatisfied(context);
        if (!mock.getExchanges().isEmpty()) {
            Exchange exchange = mock.getExchanges().get(0);
            String body = exchange.getMessage().getBody(String.class);
            JobResponse response = objectMapper.readValue(body, JobResponse.class);
            assertFalse(response.isSuccess()); // The job does not exist in Zeebe
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        createComponent();

        return new RouteBuilder() {
            public void configure() {
                from("direct:throwError")
                        .to("zeebe://throwError")
                        .to("mock:throwError");

                from("direct:throwError_JSON")
                        .to("zeebe://throwError?formatJSON=true")
                        .to("mock:throwError_JSON");
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
