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

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.model.CreateEventSourceMappingResult;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.DeleteEventSourceMappingResult;
import com.amazonaws.services.lambda.model.DeleteFunctionResult;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.ListEventSourceMappingsResult;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.services.lambda.model.ListTagsResult;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionRequest;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionResult;
import com.amazonaws.services.lambda.model.PublishVersionResult;
import com.amazonaws.services.lambda.model.TagResourceResult;
import com.amazonaws.services.lambda.model.UntagResourceResult;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.amazonaws.util.IOUtils;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class LambdaProducerTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mock;

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

        assertMockEndpointsSatisfied();

        CreateFunctionResult result = (CreateFunctionResult)exchange.getIn().getBody();
        assertEquals(result.getFunctionName(), "GetHelloWithName");
        assertEquals(result.getDescription(), "Hello with node.js on Lambda");
        assertNotNull(result.getFunctionArn());
        assertNotNull(result.getCodeSha256());
    }

    @Test
    public void lambdaUpdateFunctionTest() throws Exception {

        Exchange exchange = template.send("direct:updateFunction", ExchangePattern.InOut, new Processor() {
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

        assertMockEndpointsSatisfied();

        UpdateFunctionCodeResult result = (UpdateFunctionCodeResult)exchange.getIn().getBody();
        assertEquals(result.getFunctionName(), "GetHelloWithName");
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

        assertMockEndpointsSatisfied();
        assertNotNull(exchange.getOut().getBody(DeleteFunctionResult.class));
    }

    @Test
    public void lambdaGetFunctionTest() throws Exception {

        Exchange exchange = template.send("direct:getFunction", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });
        assertMockEndpointsSatisfied();

        GetFunctionResult result = (GetFunctionResult)exchange.getOut().getBody();
        assertEquals(result.getConfiguration().getFunctionName(), "GetHelloWithName");
    }

    @Test
    public void lambdaListFunctionsTest() throws Exception {

        Exchange exchange = template.send("direct:listFunctions", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });
        assertMockEndpointsSatisfied();

        ListFunctionsResult result = (ListFunctionsResult)exchange.getOut().getBody();
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
        assertMockEndpointsSatisfied();

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
    
    @Test
    public void lambdaListTagsTest() throws Exception {

        Exchange exchange = template.send("direct:listTags", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(LambdaConstants.RESOURCE_ARN, "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
            }
        });
        assertMockEndpointsSatisfied();

        ListTagsResult result = (ListTagsResult)exchange.getOut().getBody();
        assertEquals("lambda-tag", result.getTags().get("test"));
    }
    
    @Test
    public void tagResourceTest() throws Exception {

        Exchange exchange = template.send("direct:tagResource", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> tags = new HashMap<String, String>();
                tags.put("test", "added-tag");
                exchange.getIn().setHeader(LambdaConstants.RESOURCE_ARN, "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
                exchange.getIn().setHeader(LambdaConstants.RESOURCE_TAGS, tags);
            }
        });
        assertMockEndpointsSatisfied();

        TagResourceResult result = (TagResourceResult)exchange.getOut().getBody();
        assertNotNull(result);
    }
    
    @Test
    public void untagResourceTest() throws Exception {

        Exchange exchange = template.send("direct:untagResource", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                List<String> tagKeys = new ArrayList<String>();
                tagKeys.add("test");
                exchange.getIn().setHeader(LambdaConstants.RESOURCE_ARN, "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
                exchange.getIn().setHeader(LambdaConstants.RESOURCE_TAG_KEYS, tagKeys);
            }
        });
        assertMockEndpointsSatisfied();

        UntagResourceResult result = (UntagResourceResult)exchange.getOut().getBody();
        assertNotNull(result);
    }

    @Test
    public void publishVersionTest() throws Exception {

        Exchange exchange = template.send("direct:publishVersion", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(LambdaConstants.VERSION_DESCRIPTION, "This is my description");
            }
        });
        assertMockEndpointsSatisfied();

        PublishVersionResult result = (PublishVersionResult)exchange.getOut().getBody();
        assertNotNull(result);
        assertEquals("GetHelloWithName", result.getFunctionName());
        assertEquals("This is my description", result.getDescription());
    }
    
    @Test
    public void listVersionsTest() throws Exception {

        Exchange exchange = template.send("direct:listVersions", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(LambdaConstants.VERSION_DESCRIPTION, "This is my description");
            }
        });
        assertMockEndpointsSatisfied();

        ListVersionsByFunctionResult result = (ListVersionsByFunctionResult)exchange.getOut().getBody();
        assertNotNull(result);
        assertEquals("GetHelloWithName", result.getVersions().get(0).getFunctionName());
        assertEquals("1", result.getVersions().get(0).getVersion());
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        AmazonLambdaClientMock clientMock = new AmazonLambdaClientMock();

        registry.bind("awsLambdaClient", clientMock);

        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:createFunction").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=createFunction").to("mock:result");

                from("direct:getFunction").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=getFunction").to("mock:result");

                from("direct:listFunctions").to("aws-lambda://myFunction?awsLambdaClient=#awsLambdaClient&operation=listFunctions").to("mock:result");

                from("direct:invokeFunction").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=invokeFunction").to("mock:result");

                from("direct:deleteFunction").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=deleteFunction").to("mock:result");

                from("direct:updateFunction").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=updateFunction").to("mock:result");
                
                from("direct:listTags").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=listTags").to("mock:result");
                
                from("direct:tagResource").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=tagResource").to("mock:result");
                
                from("direct:untagResource").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=untagResource").to("mock:result");
                
                from("direct:publishVersion").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=publishVersion").to("mock:result");
                
                from("direct:listVersions").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=listVersions").to("mock:result");

                from("direct:createEventSourceMapping").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=createEventSourceMapping").to("mock:result");
                
                from("direct:deleteEventSourceMapping").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=deleteEventSourceMapping").to("mock:result");
                
                from("direct:listEventSourceMapping").to("aws-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=listEventSourceMapping").to("mock:result");
            }
        };
    }
}
