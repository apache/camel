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
import software.amazon.awssdk.services.lambda.model.DeleteFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LambdaConcurrencyIT extends Aws2LambdaBase {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void putAndGetFunctionConcurrencyTest() throws Exception {
        result.reset();
        result.expectedMessageCount(2);

        // First create a function
        template.send("direct:createFunctionForPutGet", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.RUNTIME, "nodejs16.x");
                exchange.getIn().setHeader(Lambda2Constants.HANDLER, "GetHelloWithName.handler");
                exchange.getIn().setHeader(Lambda2Constants.DESCRIPTION, "Test function for concurrency");
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

        // Put function concurrency
        template.send("direct:putFunctionConcurrency", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.RESERVED_CONCURRENT_EXECUTIONS, 10);
            }
        });

        // Get function concurrency
        template.send("direct:getFunctionConcurrency", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        PutFunctionConcurrencyResponse putResp = result.getExchanges().get(0).getIn()
                .getBody(PutFunctionConcurrencyResponse.class);
        assertEquals(10, putResp.reservedConcurrentExecutions());

        GetFunctionConcurrencyResponse getResp = result.getExchanges().get(1).getIn()
                .getBody(GetFunctionConcurrencyResponse.class);
        assertNotNull(getResp);
    }

    @Test
    public void deleteFunctionConcurrencyTest() throws Exception {
        result.reset();

        // First create a function with a different name
        template.send("direct:createFunctionForDelete", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.RUNTIME, "nodejs16.x");
                exchange.getIn().setHeader(Lambda2Constants.HANDLER, "GetHelloWithName.handler");
                exchange.getIn().setHeader(Lambda2Constants.DESCRIPTION, "Test function for delete concurrency");
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

        // Put function concurrency first
        template.send("direct:putFunctionConcurrencyForDelete", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.RESERVED_CONCURRENT_EXECUTIONS, 5);
            }
        });

        // Reset mock and set expectation for delete operation only
        result.reset();
        result.expectedMessageCount(1);

        // Delete function concurrency
        template.send("direct:deleteFunctionConcurrency", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        DeleteFunctionConcurrencyResponse resp = result.getExchanges().get(0).getIn()
                .getBody(DeleteFunctionConcurrencyResponse.class);
        assertNotNull(resp);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Routes for putAndGetFunctionConcurrencyTest
                from("direct:createFunctionForPutGet")
                        .to("aws2-lambda://ConcurrencyPutGetFunction?operation=createFunction");
                from("direct:putFunctionConcurrency")
                        .to("aws2-lambda://ConcurrencyPutGetFunction?operation=putFunctionConcurrency")
                        .to("mock:result");
                from("direct:getFunctionConcurrency")
                        .to("aws2-lambda://ConcurrencyPutGetFunction?operation=getFunctionConcurrency")
                        .to("mock:result");

                // Routes for deleteFunctionConcurrencyTest
                from("direct:createFunctionForDelete")
                        .to("aws2-lambda://ConcurrencyDeleteFunction?operation=createFunction");
                from("direct:putFunctionConcurrencyForDelete")
                        .to("aws2-lambda://ConcurrencyDeleteFunction?operation=putFunctionConcurrency");
                from("direct:deleteFunctionConcurrency")
                        .to("aws2-lambda://ConcurrencyDeleteFunction?operation=deleteFunctionConcurrency")
                        .to("mock:result");
            }
        };
    }
}
