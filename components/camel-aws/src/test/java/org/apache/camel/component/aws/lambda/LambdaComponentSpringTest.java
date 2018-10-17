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
package org.apache.camel.component.aws.lambda;

import java.io.*;

import com.amazonaws.services.lambda.model.CreateEventSourceMappingResult;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.DeleteEventSourceMappingResult;
import com.amazonaws.services.lambda.model.DeleteFunctionResult;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.ListEventSourceMappingsResult;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.util.IOUtils;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class LambdaComponentSpringTest extends CamelSpringTestSupport {

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

        CreateFunctionResult result = (CreateFunctionResult) exchange.getOut().getBody();
        assertEquals(result.getFunctionName(), "GetHelloWithName");
        assertEquals(result.getDescription(), "Hello with node.js on Lambda");
        assertNotNull(result.getFunctionArn());
        assertNotNull(result.getCodeSha256());
    }

    @Test
    public void lambdaDeleteFunctionTest() throws Exception {

        Exchange exchange = template.send("direct:deleteFunction", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });
        assertNotNull(exchange.getOut().getBody(DeleteFunctionResult.class));
    }


    @Test
    public void lambdaGetFunctionTest() throws Exception {

        Exchange exchange = template.send("direct:getFunction", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });
        GetFunctionResult result = (GetFunctionResult) exchange.getOut().getBody();
        assertEquals(result.getConfiguration().getFunctionName(), "GetHelloWithName");
    }


    @Test
    public void lambdaListFunctionsTest() throws Exception {
        Exchange exchange = template.send("direct:listFunctions", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });

        ListFunctionsResult result = (ListFunctionsResult) exchange.getOut().getBody();
        assertEquals(result.getFunctions().size(), 1);
        assertEquals(result.getFunctions().get(0).getFunctionName(), "GetHelloWithName");
    }


    @Test
    public void lambdaInvokeFunctionTest() throws Exception {
        Exchange exchange = template.send("direct:invokeFunction", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("{\"name\":\"Camel\"}");
            }
        });

        assertNotNull(exchange.getOut().getBody(String.class));
        assertEquals(exchange.getOut().getBody(String.class), "{\"Hello\":\"Camel\"}");
    }
    
    @Test
    public void lambdaCreateEventSourceMappingTest() throws Exception {
        Exchange exchange = template.send("direct:createEventSourceMapping", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(LambdaConstants.EVENT_SOURCE_ARN, "arn:aws:sqs:eu-central-1:643534317684:testqueue");
                exchange.getIn().setHeader(LambdaConstants.EVENT_SOURCE_BATCH_SIZE, 100);
            }
        });
        assertMockEndpointsSatisfied();

        CreateEventSourceMappingResult result = exchange.getOut().getBody(CreateEventSourceMappingResult.class);
        assertEquals(result.getFunctionArn(), "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
    }
    
    @Test
    public void lambdaDeleteEventSourceMappingTest() throws Exception {
        Exchange exchange = template.send("direct:deleteEventSourceMapping", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(LambdaConstants.EVENT_SOURCE_UUID, "a1239494949382882383");
            }
        });
        assertMockEndpointsSatisfied();

        DeleteEventSourceMappingResult result = exchange.getOut().getBody(DeleteEventSourceMappingResult.class);
        assertTrue(result.getState().equalsIgnoreCase("Deleting"));
    }
    
    @Test
    public void lambdaListEventSourceMappingTest() throws Exception {
        Exchange exchange = template.send("direct:listEventSourceMapping", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
            }
        });
        assertMockEndpointsSatisfied();

        ListEventSourceMappingsResult result = exchange.getOut().getBody(ListEventSourceMappingsResult.class);
        assertEquals(result.getEventSourceMappings().get(0).getFunctionArn(), "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
            "org/apache/camel/component/aws/lambda/LambdaComponentSpringTest-context.xml");
    }
}