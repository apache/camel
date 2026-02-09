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
import software.amazon.awssdk.services.lambda.model.AddPermissionResponse;
import software.amazon.awssdk.services.lambda.model.GetPolicyResponse;
import software.amazon.awssdk.services.lambda.model.RemovePermissionResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LambdaPermissionsIT extends Aws2LambdaBase {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void addAndGetPermissionTest() throws Exception {
        result.reset();
        result.expectedMessageCount(2);

        // First create a function
        template.send("direct:createFunctionForAddGet", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.RUNTIME, "nodejs16.x");
                exchange.getIn().setHeader(Lambda2Constants.HANDLER, "GetHelloWithName.handler");
                exchange.getIn().setHeader(Lambda2Constants.DESCRIPTION, "Test function for permissions");
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

        // Add permission
        template.send("direct:addPermission", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.STATEMENT_ID, "s3-invoke-test");
                exchange.getIn().setHeader(Lambda2Constants.ACTION, "lambda:InvokeFunction");
                exchange.getIn().setHeader(Lambda2Constants.PRINCIPAL, "s3.amazonaws.com");
            }
        });

        // Get policy
        template.send("direct:getPolicy", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        AddPermissionResponse addResp = result.getExchanges().get(0).getIn()
                .getBody(AddPermissionResponse.class);
        assertNotNull(addResp);
        assertNotNull(addResp.statement());

        GetPolicyResponse policyResp = result.getExchanges().get(1).getIn()
                .getBody(GetPolicyResponse.class);
        assertNotNull(policyResp);
        assertNotNull(policyResp.policy());
        assertTrue(policyResp.policy().contains("s3-invoke-test"));
    }

    @Test
    public void removePermissionTest() throws Exception {
        result.reset();

        // First create a function with a different name
        template.send("direct:createFunctionForRemove", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.RUNTIME, "nodejs16.x");
                exchange.getIn().setHeader(Lambda2Constants.HANDLER, "GetHelloWithName.handler");
                exchange.getIn().setHeader(Lambda2Constants.DESCRIPTION, "Test function for remove permission");
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

        // Add permission first
        template.send("direct:addPermissionForRemove", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.STATEMENT_ID, "s3-invoke-remove");
                exchange.getIn().setHeader(Lambda2Constants.ACTION, "lambda:InvokeFunction");
                exchange.getIn().setHeader(Lambda2Constants.PRINCIPAL, "s3.amazonaws.com");
            }
        });

        // Reset mock and set expectation for remove operation only
        result.reset();
        result.expectedMessageCount(1);

        // Remove permission
        template.send("direct:removePermission", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.STATEMENT_ID, "s3-invoke-remove");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        RemovePermissionResponse resp = result.getExchanges().get(0).getIn()
                .getBody(RemovePermissionResponse.class);
        assertNotNull(resp);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Routes for addAndGetPermissionTest
                from("direct:createFunctionForAddGet")
                        .to("aws2-lambda://PermissionAddGetFunction?operation=createFunction");
                from("direct:addPermission")
                        .to("aws2-lambda://PermissionAddGetFunction?operation=addPermission")
                        .to("mock:result");
                from("direct:getPolicy")
                        .to("aws2-lambda://PermissionAddGetFunction?operation=getPolicy")
                        .to("mock:result");

                // Routes for removePermissionTest
                from("direct:createFunctionForRemove")
                        .to("aws2-lambda://PermissionRemoveFunction?operation=createFunction");
                from("direct:addPermissionForRemove")
                        .to("aws2-lambda://PermissionRemoveFunction?operation=addPermission");
                from("direct:removePermission")
                        .to("aws2-lambda://PermissionRemoveFunction?operation=removePermission")
                        .to("mock:result");
            }
        };
    }
}
