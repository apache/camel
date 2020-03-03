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

import java.io.*;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.lambda.Lambda2Constants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;

@Ignore("Must be manually tested. Provide your own accessKey and secretKey!")
public class LambdaComponentIntegrationTest extends CamelTestSupport {

    @Test
    public void lambdaCreateFunctionTest() throws Exception {
        Exchange exchange = template.send("direct:createFunction", ExchangePattern.InOut, new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.RUNTIME, "nodejs6.10");
                exchange.getIn().setHeader(Lambda2Constants.HANDLER, "GetHelloWithName.handler");
                exchange.getIn().setHeader(Lambda2Constants.DESCRIPTION, "Hello with node.js on Lambda");
                exchange.getIn().setHeader(Lambda2Constants.ROLE, "arn:aws:iam::643534317684:role/lambda-execution-role");

                ClassLoader classLoader = getClass().getClassLoader();
                File file = new File(classLoader.getResource("org/apache/camel/component/aws/lambda/function/node/GetHelloWithName.zip").getFile());
                FileInputStream inputStream = new FileInputStream(file);
                exchange.getIn().setBody(SdkBytes.fromInputStream(inputStream));
            }
        });
        assertNotNull(exchange.getMessage().getBody(CreateFunctionResponse.class));
        assertEquals(exchange.getMessage().getBody(CreateFunctionResponse.class).functionName(), "GetHelloWithName");
    }

    @Test
    public void lambdaListFunctionsTest() throws Exception {
        Exchange exchange = template.send("direct:listFunctions", ExchangePattern.InOut, new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });
        assertNotNull(exchange.getMessage().getBody(ListFunctionsResponse.class));
        assertEquals(exchange.getMessage().getBody(ListFunctionsResponse.class).functions().size(), 3);
    }

    @Test
    public void lambdaGetFunctionTest() throws Exception {
        Exchange exchange = template.send("direct:getFunction", ExchangePattern.InOut, new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });
        GetFunctionResponse result = exchange.getMessage().getBody(GetFunctionResponse.class);
        assertNotNull(result);
        assertEquals(result.configuration().functionName(), "GetHelloWithName");
        assertEquals(result.configuration().runtime(), "nodejs6.10");

    }

    @Test
    public void lambdaInvokeFunctionTest() throws Exception {
        Exchange exchange = template.send("direct:invokeFunction", ExchangePattern.InOut, new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("{\"name\":\"Camel\"}");
            }
        });

        assertNotNull(exchange.getMessage().getBody(String.class));
        assertEquals(exchange.getMessage().getBody(String.class), "{\"Hello\":\"Camel\"}");
    }

    @Test
    public void lambdaDeleteFunctionTest() throws Exception {

        Exchange exchange = template.send("direct:deleteFunction", ExchangePattern.InOut, new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
            }
        });
        assertNotNull(exchange.getMessage().getBody(DeleteFunctionResponse.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:createFunction")
                    .to("aws2-lambda://GetHelloWithName?operation=createFunction&accessKey=xxxx&secretKey=xxxx&awsLambdaEndpoint=lambda.eu-central-1.amazonaws.com");

                from("direct:listFunctions")
                    .to("aws2-lambda://myFunction?operation=listFunctions&accessKey=xxxx&secretKey=xxxx&awsLambdaEndpoint=lambda.eu-central-1.amazonaws.com");

                from("direct:getFunction")
                    .to("aws2-lambda://GetHelloWithName?operation=getFunction&accessKey=xxxx&secretKey=xxxx&awsLambdaEndpoint=lambda.eu-central-1.amazonaws.com");

                from("direct:invokeFunction")
                    .to("aws2-lambda://GetHelloWithName?operation=invokeFunction&accessKey=xxxx&secretKey=xxxx&awsLambdaEndpoint=lambda.eu-central-1.amazonaws.com");

                from("direct:deleteFunction")
                    .to("aws2-lambda://GetHelloWithName?operation=deleteFunction&accessKey=xxxx&secretKey=xxxx&awsLambdaEndpoint=lambda.eu-central-1.amazonaws.com");

            }
        };
    }
}
