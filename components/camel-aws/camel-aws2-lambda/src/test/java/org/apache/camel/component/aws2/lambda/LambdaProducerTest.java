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
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.model.AddPermissionResponse;
import software.amazon.awssdk.services.lambda.model.CreateAliasResponse;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigResponse;
import software.amazon.awssdk.services.lambda.model.DeleteAliasResponse;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionUrlConfigResponse;
import software.amazon.awssdk.services.lambda.model.GetAliasResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionUrlConfigResponse;
import software.amazon.awssdk.services.lambda.model.GetPolicyResponse;
import software.amazon.awssdk.services.lambda.model.ListAliasesResponse;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionUrlConfigsResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.ListTagsResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.RemovePermissionResponse;
import software.amazon.awssdk.services.lambda.model.TagResourceResponse;
import software.amazon.awssdk.services.lambda.model.UntagResourceResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionUrlConfigResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LambdaProducerTest extends CamelTestSupport {

    @BindToRegistry("awsLambdaClient")
    AmazonLambdaClientMock clientMock = new AmazonLambdaClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void lambdaCreateFunctionTest() {

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
    public void lambdaDeleteFunctionTest() {

        Exchange exchange = template.send("direct:deleteFunction", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {

            }
        });
        assertNotNull(exchange.getMessage().getBody(DeleteFunctionResponse.class));
    }

    @Test
    public void lambdaGetFunctionTest() {

        Exchange exchange = template.send("direct:getFunction", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {

            }
        });
        GetFunctionResponse result = (GetFunctionResponse) exchange.getMessage().getBody();
        assertEquals("GetHelloWithName", result.configuration().functionName());
    }

    @Test
    public void lambdaGetFunctionPojoTest() {

        Exchange exchange = template.send("direct:getFunctionPojo", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(GetFunctionRequest.builder().functionName("GetHelloWithName").build());
            }
        });
        GetFunctionResponse result = (GetFunctionResponse) exchange.getMessage().getBody();
        assertEquals("GetHelloWithName", result.configuration().functionName());
    }

    @Test
    public void lambdaListFunctionsTest() {
        Exchange exchange = template.send("direct:listFunctions", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {

            }
        });

        ListFunctionsResponse result = (ListFunctionsResponse) exchange.getMessage().getBody();
        assertEquals(1, result.functions().size());
        assertEquals("GetHelloWithName", result.functions().get(0).functionName());
    }

    @Test
    public void lambdaInvokeFunctionTest() {
        Exchange exchange = template.send("direct:invokeFunction", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
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
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.EVENT_SOURCE_ARN,
                        "arn:aws:sqs:eu-central-1:643534317684:testqueue");
                exchange.getIn().setHeader(Lambda2Constants.EVENT_SOURCE_BATCH_SIZE, 100);
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        CreateEventSourceMappingResponse result = exchange.getMessage().getBody(CreateEventSourceMappingResponse.class);
        assertEquals("arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName", result.functionArn());
    }

    @Test
    public void lambdaDeleteEventSourceMappingTest() throws Exception {
        Exchange exchange = template.send("direct:deleteEventSourceMapping", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.EVENT_SOURCE_UUID, "a1239494949382882383");
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        DeleteEventSourceMappingResponse result = exchange.getMessage().getBody(DeleteEventSourceMappingResponse.class);
        assertTrue(result.state().equalsIgnoreCase("Deleting"));
    }

    @Test
    public void lambdaListEventSourceMappingTest() throws Exception {
        Exchange exchange = template.send("direct:listEventSourceMapping", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        ListEventSourceMappingsResponse result = exchange.getMessage().getBody(ListEventSourceMappingsResponse.class);
        assertEquals("arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName",
                result.eventSourceMappings().get(0).functionArn());
    }

    @Test
    public void lambdaListTagsTest() throws Exception {

        Exchange exchange = template.send("direct:listTags", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_ARN,
                        "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        ListTagsResponse result = (ListTagsResponse) exchange.getMessage().getBody();
        assertEquals("lambda-tag", result.tags().get("test"));
    }

    @Test
    public void tagResourceTest() throws Exception {

        Exchange exchange = template.send("direct:tagResource", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                Map<String, String> tags = new HashMap<>();
                tags.put("test", "added-tag");
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_ARN,
                        "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_TAGS, tags);
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        TagResourceResponse result = (TagResourceResponse) exchange.getMessage().getBody();
        assertNotNull(result);
    }

    @Test
    public void untagResourceTest() throws Exception {

        Exchange exchange = template.send("direct:untagResource", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                List<String> tagKeys = new ArrayList<>();
                tagKeys.add("test");
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_ARN,
                        "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_TAG_KEYS, tagKeys);
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        UntagResourceResponse result = (UntagResourceResponse) exchange.getMessage().getBody();
        assertNotNull(result);
    }

    @Test
    public void publishVersionTest() throws Exception {

        Exchange exchange = template.send("direct:publishVersion", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.VERSION_DESCRIPTION, "This is my description");
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        PublishVersionResponse result = (PublishVersionResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals("GetHelloWithName", result.functionName());
        assertEquals("This is my description", result.description());
    }

    @Test
    public void listVersionsTest() throws Exception {

        Exchange exchange = template.send("direct:listVersions", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.VERSION_DESCRIPTION, "This is my description");
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        ListVersionsByFunctionResponse result = (ListVersionsByFunctionResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals("GetHelloWithName", result.versions().get(0).functionName());
        assertEquals("1", result.versions().get(0).version());
    }

    @Test
    public void createAliasTest() throws Exception {

        Exchange exchange = template.send("direct:createAlias", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_ALIAS_DESCRIPTION, "an alias");
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_ALIAS_NAME, "alias");
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_VERSION, "1");
            }
        });
        MockEndpoint.assertIsSatisfied(context);

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
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_ALIAS_NAME, "alias");
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        DeleteAliasResponse result = (DeleteAliasResponse) exchange.getMessage().getBody();
        assertNotNull(result);
    }

    @Test
    public void getAliasTest() throws Exception {

        Exchange exchange = template.send("direct:getAlias", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_ALIAS_NAME, "alias");
            }
        });
        MockEndpoint.assertIsSatisfied(context);

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
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_VERSION, "1");
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        ListAliasesResponse result = (ListAliasesResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals("an alias", result.aliases().get(0).description());
        assertEquals("alias", result.aliases().get(0).name());
        assertEquals("1", result.aliases().get(0).functionVersion());
    }

    // Function URL tests

    @Test
    public void createFunctionUrlConfigTest() throws Exception {

        Exchange exchange = template.send("direct:createFunctionUrlConfig", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_URL_AUTH_TYPE, "NONE");
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        CreateFunctionUrlConfigResponse result = (CreateFunctionUrlConfigResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertNotNull(result.functionUrl());
        assertTrue(result.functionUrl().contains("lambda-url"));
    }

    @Test
    public void getFunctionUrlConfigTest() throws Exception {

        Exchange exchange = template.send("direct:getFunctionUrlConfig", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        GetFunctionUrlConfigResponse result = (GetFunctionUrlConfigResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertNotNull(result.functionUrl());
        assertTrue(result.functionUrl().contains("lambda-url"));
    }

    @Test
    public void updateFunctionUrlConfigTest() throws Exception {

        Exchange exchange = template.send("direct:updateFunctionUrlConfig", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_URL_AUTH_TYPE, "AWS_IAM");
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        UpdateFunctionUrlConfigResponse result = (UpdateFunctionUrlConfigResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertNotNull(result.functionUrl());
        assertTrue(result.functionUrl().contains("lambda-url"));
    }

    @Test
    public void deleteFunctionUrlConfigTest() throws Exception {

        Exchange exchange = template.send("direct:deleteFunctionUrlConfig", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        DeleteFunctionUrlConfigResponse result = (DeleteFunctionUrlConfigResponse) exchange.getMessage().getBody();
        assertNotNull(result);
    }

    @Test
    public void listFunctionUrlConfigsTest() throws Exception {

        Exchange exchange = template.send("direct:listFunctionUrlConfigs", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        ListFunctionUrlConfigsResponse result = (ListFunctionUrlConfigsResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals(1, result.functionUrlConfigs().size());
        assertTrue(result.functionUrlConfigs().get(0).functionUrl().contains("lambda-url"));
    }

    // Function Configuration tests

    @Test
    public void getFunctionConfigurationTest() throws Exception {

        Exchange exchange = template.send("direct:getFunctionConfiguration", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        GetFunctionConfigurationResponse result = (GetFunctionConfigurationResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals("GetHelloWithName", result.functionName());
        assertNotNull(result.functionArn());
        assertEquals(128, result.memorySize());
    }

    @Test
    public void updateFunctionConfigurationTest() throws Exception {

        Exchange exchange = template.send("direct:updateFunctionConfiguration", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_MEMORY_SIZE, 256);
                exchange.getIn().setHeader(Lambda2Constants.FUNCTION_TIMEOUT, 30);
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        UpdateFunctionConfigurationResponse result = (UpdateFunctionConfigurationResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals("GetHelloWithName", result.functionName());
        assertNotNull(result.functionArn());
        assertEquals(256, result.memorySize());
        assertEquals(30, result.timeout());
    }

    // Concurrency tests

    @Test
    public void putFunctionConcurrencyTest() throws Exception {

        Exchange exchange = template.send("direct:putFunctionConcurrency", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.RESERVED_CONCURRENT_EXECUTIONS, 100);
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        PutFunctionConcurrencyResponse result = (PutFunctionConcurrencyResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals(100, result.reservedConcurrentExecutions());
    }

    @Test
    public void deleteFunctionConcurrencyTest() throws Exception {

        Exchange exchange = template.send("direct:deleteFunctionConcurrency", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        DeleteFunctionConcurrencyResponse result = (DeleteFunctionConcurrencyResponse) exchange.getMessage().getBody();
        assertNotNull(result);
    }

    @Test
    public void getFunctionConcurrencyTest() throws Exception {

        Exchange exchange = template.send("direct:getFunctionConcurrency", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        GetFunctionConcurrencyResponse result = (GetFunctionConcurrencyResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals(100, result.reservedConcurrentExecutions());
    }

    // Permission tests

    @Test
    public void addPermissionTest() throws Exception {

        Exchange exchange = template.send("direct:addPermission", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.STATEMENT_ID, "s3-invoke");
                exchange.getIn().setHeader(Lambda2Constants.ACTION, "lambda:InvokeFunction");
                exchange.getIn().setHeader(Lambda2Constants.PRINCIPAL, "s3.amazonaws.com");
                exchange.getIn().setHeader(Lambda2Constants.SOURCE_ARN, "arn:aws:s3:::my-bucket");
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        AddPermissionResponse result = (AddPermissionResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertNotNull(result.statement());
        assertTrue(result.statement().contains("s3-invoke"));
    }

    @Test
    public void removePermissionTest() throws Exception {

        Exchange exchange = template.send("direct:removePermission", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Lambda2Constants.STATEMENT_ID, "s3-invoke");
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        RemovePermissionResponse result = (RemovePermissionResponse) exchange.getMessage().getBody();
        assertNotNull(result);
    }

    @Test
    public void getPolicyTest() throws Exception {

        Exchange exchange = template.send("direct:getPolicy", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        GetPolicyResponse result = (GetPolicyResponse) exchange.getMessage().getBody();
        assertNotNull(result);
        assertNotNull(result.policy());
        assertTrue(result.policy().contains("Statement"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
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

                // Function URL routes
                from("direct:createFunctionUrlConfig").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=createFunctionUrlConfig")
                        .to("mock:result");

                from("direct:getFunctionUrlConfig").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=getFunctionUrlConfig")
                        .to("mock:result");

                from("direct:updateFunctionUrlConfig").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=updateFunctionUrlConfig")
                        .to("mock:result");

                from("direct:deleteFunctionUrlConfig").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=deleteFunctionUrlConfig")
                        .to("mock:result");

                from("direct:listFunctionUrlConfigs").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=listFunctionUrlConfigs")
                        .to("mock:result");

                // Function configuration routes
                from("direct:getFunctionConfiguration").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=getFunctionConfiguration")
                        .to("mock:result");

                from("direct:updateFunctionConfiguration").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=updateFunctionConfiguration")
                        .to("mock:result");

                // Concurrency routes
                from("direct:putFunctionConcurrency").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=putFunctionConcurrency")
                        .to("mock:result");

                from("direct:deleteFunctionConcurrency").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=deleteFunctionConcurrency")
                        .to("mock:result");

                from("direct:getFunctionConcurrency").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=getFunctionConcurrency")
                        .to("mock:result");

                // Permission routes
                from("direct:addPermission").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=addPermission")
                        .to("mock:result");

                from("direct:removePermission").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=removePermission")
                        .to("mock:result");

                from("direct:getPolicy").to(
                        "aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=getPolicy")
                        .to("mock:result");
            }
        };
    }
}
