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
package org.apache.camel.component.aws.lambda.integration;

import java.io.*;

import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.DeleteFunctionResult;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.util.IOUtils;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.lambda.LambdaConstants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must be manually tested. Provide your own accessKey and secretKey!")
public class LambdaComponentIntegrationTest extends CamelTestSupport {


    @Test
    public void lambdaCreateFunctionTest() throws Exception {
        Exchange exchange = template.send("direct:createFunction", ExchangePattern.InOut, new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(LambdaConstants.RUNTIME, "nodejs6.10");
                exchange.getIn().setHeader(LambdaConstants.HANDLER, "GetHelloWithName.handler");
                exchange.getIn().setHeader(LambdaConstants.DESCRIPTION, "Hello with node.js on Lambda");
                exchange.getIn().setHeader(LambdaConstants.ROLE, "arn:aws:iam::643534317684:role/lambda-execution-role");

                ClassLoader classLoader = getClass().getClassLoader();
                File file = new File(classLoader.getResource("org/apache/camel/component/aws/lambda/function/node/GetHelloWithName.zip").getFile());
                FileInputStream inputStream = new FileInputStream(file);
                exchange.getIn().setBody(IOUtils.toByteArray(inputStream));
            }
        });
        assertNotNull(exchange.getMessage().getBody(CreateFunctionResult.class));
        assertEquals(exchange.getMessage().getBody(CreateFunctionResult.class).getFunctionName(), "GetHelloWithName");
    }

    @Test
    public void lambdaListFunctionsTest() throws Exception {
        Exchange exchange = template.send("direct:listFunctions", ExchangePattern.InOut, new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });
        assertNotNull(exchange.getMessage().getBody(ListFunctionsResult.class));
        assertEquals(exchange.getMessage().getBody(ListFunctionsResult.class).getFunctions().size(), 3);
    }


    @Test
    public void lambdaGetFunctionTest() throws Exception {
        Exchange exchange = template.send("direct:getFunction", ExchangePattern.InOut, new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });
        GetFunctionResult result = exchange.getMessage().getBody(GetFunctionResult.class);
        assertNotNull(result);
        assertEquals(result.getConfiguration().getFunctionName(), "GetHelloWithName");
        assertEquals(result.getConfiguration().getRuntime(), "nodejs6.10");

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
        assertNotNull(exchange.getMessage().getBody(DeleteFunctionResult.class));
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {


                from("direct:createFunction")
                    .to("aws-lambda://GetHelloWithName?operation=createFunction&accessKey=xxxx&secretKey=xxxx&awsLambdaEndpoint=lambda.eu-central-1.amazonaws.com");

                from("direct:listFunctions")
                    .to("aws-lambda://myFunction?operation=listFunctions&accessKey=xxxx&secretKey=xxxx&awsLambdaEndpoint=lambda.eu-central-1.amazonaws.com");

                from("direct:getFunction")
                    .to("aws-lambda://GetHelloWithName?operation=getFunction&accessKey=xxxx&secretKey=xxxx&awsLambdaEndpoint=lambda.eu-central-1.amazonaws.com");

                from("direct:invokeFunction")
                    .to("aws-lambda://GetHelloWithName?operation=invokeFunction&accessKey=xxxx&secretKey=xxxx&awsLambdaEndpoint=lambda.eu-central-1.amazonaws.com");

                from("direct:deleteFunction")
                    .to("aws-lambda://GetHelloWithName?operation=deleteFunction&accessKey=xxxx&secretKey=xxxx&awsLambdaEndpoint=lambda.eu-central-1.amazonaws.com");

            }
        };
    }
}
