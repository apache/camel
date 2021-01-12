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
package org.apache.camel.component.aws2.lambda;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.model.CreateAliasResponse;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteAliasResponse;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetAliasResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.ListAliasesResponse;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.ListTagsResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;
import software.amazon.awssdk.services.lambda.model.TagResourceResponse;
import software.amazon.awssdk.services.lambda.model.UntagResourceResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LambdaProducerTest extends CamelTestSupport {

    @BindToRegistry("awsLambdaClient")
    AmazonLambdaClientMock clientMock = new AmazonLambdaClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

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
                File file = new File(
                        classLoader.getResource("org/apache/camel/component/aws2/lambda/function/node/GetHelloWithName.zip")
                                .getFile());
                FileInputStream inputStream = new FileInputStream(file);
                exchange.getIn().setBody(inputStream);
            }
        });

        CreateFunctionResponse result = (CreateFunctionResponse) exchange.getMessage().getBody();
        assertEquals("GetHelloWithName", result.functionName());
        assertEquals("Hello with node.js on Lambda", result.description());
        assertNotNull(result.functionArn());
        assertNotNull(result.codeSha256());
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

    @Test
    public void lambdaGetFunctionTest() throws Exception {

        Exchange exchange = template.send("direct:getFunction", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });
        GetFunctionResponse result = (GetFunctionResponse) exchange.getMessage().getBody();
        assertEquals("GetHelloWithName", result.configuration().functionName());
    }

    @Test
    public void lambdaGetFunctionPojoTest() throws Exception {

        Exchange exchange = template.send("direct:getFunctionPojo", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(GetFunctionRequest.builder().functionName("GetHelloWithName").build());
            }
        });
        GetFunctionResponse result = (GetFunctionResponse) exchange.getMessage().getBody();
        assertEquals("GetHelloWithName", result.configuration().functionName());
    }

    @Test
    public void lambdaListFunctionsTest() throws Exception {
        Exchange exchange = template.send("direct:listFunctions", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });

        ListFunctionsResponse result = (ListFunctionsResponse) exchange.getMessage().getBody();
        assertEquals(1, result.functions().size());
        assertEquals("GetHelloWithName", result.functions().get(0).functionName());
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
        assertEquals("{\"Hello\":\"Camel\"}", exchange.getMessage().getBody(String.class));
    }

    @Test
    public void lambdaCreateEventSourceMappingTest() throws Exception {
        Exchange exchange = template.send("direct:createEventSourceMapping", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.EVENT_SOURCE_ARN,
                        "arn:aws:sqs:eu-central-1:643534317684:testqueue");
                exchange.getIn().setHeader(Lambda2Constants.EVENT_SOURCE_BATCH_SIZE, 100);
            }
        });
        assertMockEndpointsSatisfied();

        CreateEventSourceMappingResponse result = exchange.getMessage().getBody(CreateEventSourceMappingResponse.class);
        assertEquals("arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName", result.functionArn());
    }

    @Test
    public void lambdaDeleteEventSourceMappingTest() throws Exception {
        Exchange exchange = template.send("direct:deleteEventSourceMapping", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.EVENT_SOURCE_UUID, "a1239494949382882383");
            }
        });
        assertMockEndpointsSatisfied();

        DeleteEventSourceMappingResponse result = exchange.getMessage().getBody(DeleteEventSourceMappingResponse.class);
        assertTrue(result.state().equalsIgnoreCase("Deleting"));
    }

    @Test
    public void lambdaListEventSourceMappingTest() throws Exception {
        Exchange exchange = template.send("direct:listEventSourceMapping", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
            }
        });
        assertMockEndpointsSatisfied();

        ListEventSourceMappingsResponse result = exchange.getMessage().getBody(ListEventSourceMappingsResponse.class);
        assertEquals("arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName",
                result.eventSourceMappings().get(0).functionArn());
    }

    @Test
    public void lambdaListTagsTest() throws Exception {

        Exchange exchange = template.send("direct:listTags", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_ARN,
                        "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
            }
        });
        assertMockEndpointsSatisfied();

        ListTagsResponse result = (ListTagsResponse) exchange.getMessage().getBody();
        assertEquals("lambda-tag", result.tags().get("test"));
    }

    @Test
    public void tagResourceTest() throws Exception {

        Exchange exchange = template.send("direct:tagResource", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> tags = new HashMap<>();
                tags.put("test", "added-tag");
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_ARN,
                        "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_TAGS, tags);
            }
        });
        assertMockEndpointsSatisfied();

        TagResourceResponse result = (TagResourceResponse) exchange.getMessage().getBody();
        assertNotNull(result);
    }

    @Test
    public void untagResourceTest() throws Exception {

        Exchange exchange = template.send("direct:untagResource", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                List<String> tagKeys = new ArrayList<>();
                tagKeys.add("test");
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_ARN,
                        "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_TAG_KEYS, tagKeys);
            }
        });
        assertMockEndpointsSatisfied();

        UntagResourceResponse result = (UntagResourceResponse) exchange.getMessage().getBody();
        assertNotNull(result);
    }

    @Test
    public void publishVersionTest() throws Exception {

        Exchange exchange = template.send("direct:publishVersion", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.VERSION_DESCRIPTION, "This is my description");
            }
        });
        assertMockEndpointsSatisfied();

        PublishVersionResponse result = (PublishVersionResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals("GetHelloWithName", result.functionName());
        assertEquals("This is my description", result.description());
    }

    @Test
    public void listVersionsTest() throws Exception {

        Exchange exchange = template.send("direct:listVersions", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.VERSION_DESCRIPTION, "This is my description");
            }
        });
        assertMockEndpointsSatisfied();

        ListVersionsByFunctionResponse result = (ListVersionsByFunctionResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals("GetHelloWithName", result.versions().get(0).functionName());
        assertEquals("1", result.versions().get(0).version());
    }

    @Test
    public void createAliasTest() throws Exception {

        Exchange exchange = template.send("direct:createAlias", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_ALIAS_DESCRIPTION, "an alias");
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_ALIAS_NAME, "alias");
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_VERSION, "1");
            }
        });
        assertMockEndpointsSatisfied();

        CreateAliasResponse result = (CreateAliasResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals("an alias", result.description());
        assertEquals("alias", result.name());
        assertEquals("1", result.functionVersion());
    }

    @Test
    public void deleteAliasTest() throws Exception {

        Exchange exchange = template.send("direct:deleteAlias", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_ALIAS_NAME, "alias");
            }
        });
        assertMockEndpointsSatisfied();

        DeleteAliasResponse result = (DeleteAliasResponse) exchange.getMessage().getBody();
        assertNotNull(result);
    }

    @Test
    public void getAliasTest() throws Exception {

        Exchange exchange = template.send("direct:getAlias", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_ALIAS_NAME, "alias");
            }
        });
        assertMockEndpointsSatisfied();

        GetAliasResponse result = (GetAliasResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals("an alias", result.description());
        assertEquals("alias", result.name());
        assertEquals("1", result.functionVersion());
    }

    @Test
    public void listAliasesTest() throws Exception {

        Exchange exchange = template.send("direct:listAliases", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_VERSION, "1");
            }
        });
        assertMockEndpointsSatisfied();

        ListAliasesResponse result = (ListAliasesResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals("an alias", result.aliases().get(0).description());
        assertEquals("alias", result.aliases().get(0).name());
        assertEquals("1", result.aliases().get(0).functionVersion());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:createFunction")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=createFunction")
                        .to("mock:result");

                from("direct:getFunction")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=getFunction")
                        .to("mock:result");

                from("direct:getFunctionPojo").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=getFunction&pojoRequest=true")
                        .to("mock:result");

                from("direct:listFunctions")
                        .to("aws2-lambda://myFunction?awsLambdaClient=#awsLambdaClient&operation=listFunctions")
                        .to("mock:result");

                from("direct:invokeFunction")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=invokeFunction")
                        .to("mock:result");

                from("direct:deleteFunction")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=deleteFunction")
                        .to("mock:result");

                from("direct:updateFunction")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=updateFunction")
                        .to("mock:result");

                from("direct:listTags").to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=listTags")
                        .to("mock:result");

                from("direct:tagResource")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=tagResource")
                        .to("mock:result");

                from("direct:untagResource")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=untagResource")
                        .to("mock:result");

                from("direct:publishVersion")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=publishVersion")
                        .to("mock:result");

                from("direct:listVersions")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=listVersions")
                        .to("mock:result");

                from("direct:createEventSourceMapping").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=createEventSourceMapping")
                        .to("mock:result");

                from("direct:deleteEventSourceMapping").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=deleteEventSourceMapping")
                        .to("mock:result");

                from("direct:listEventSourceMapping")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=listEventSourceMapping")
                        .to("mock:result");

                from("direct:createAlias")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=createAlias")
                        .to("mock:result");

                from("direct:deleteAlias")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=deleteAlias")
                        .to("mock:result");

                from("direct:getAlias").to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=getAlias")
                        .to("mock:result");

                from("direct:listAliases")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=listAliases")
                        .to("mock:result");
            }
        };
    }
}
