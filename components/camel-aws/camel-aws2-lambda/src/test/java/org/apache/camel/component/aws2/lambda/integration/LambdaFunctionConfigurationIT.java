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
package org.apache.camel.component.aws2.lambda.integration;

import java.io.File;
import java.io.FileInputStream;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.lambda.Lambda2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LambdaFunctionConfigurationIT extends Aws2LambdaBase {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void getFunctionConfigurationTest() throws Exception {
        result.reset();
        result.expectedMessageCount(1);

        // First create a function
        template.send("direct:createFunctionForGet", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.RUNTIME, "nodejs16.x");
                exchange.getIn().setHeader(Lambda2Constants.HANDLER, "GetHelloWithName.handler");
                exchange.getIn().setHeader(Lambda2Constants.DESCRIPTION, "Test function for configuration");
                exchange.getIn().setHeader(Lambda2Constants.ROLE,
                        "arn:aws:iam::643534317684:role/lambda-execution-role");

                ClassLoader classLoader = getClass().getClassLoader();
                File file = new File(
                        classLoader
                                .getResource("org/apache/camel/component/aws2/lambda/function/node/GetHelloWithName.zip")
                                .getFile());
                FileInputStream inputStream = new FileInputStream(file);
                exchange.getIn().setBody(inputStream);
            }
        });

        // Get function configuration
        template.send("direct:getFunctionConfiguration", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        GetFunctionConfigurationResponse resp = result.getExchanges().get(0).getIn()
                .getBody(GetFunctionConfigurationResponse.class);
        assertEquals("GetConfigFunction", resp.functionName());
        assertNotNull(resp.functionArn());
        assertNotNull(resp.memorySize());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createFunctionForGet")
                        .to("aws2-lambda://GetConfigFunction?operation=createFunction");
                from("direct:getFunctionConfiguration")
                        .to("aws2-lambda://GetConfigFunction?operation=getFunctionConfiguration")
                        .to("mock:result");
            }
        };
    }
}
