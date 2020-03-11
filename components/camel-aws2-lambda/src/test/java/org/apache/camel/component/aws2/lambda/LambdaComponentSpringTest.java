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

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import software.amazon.awssdk.services.lambda.model.CreateAliasResponse;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteAliasResponse;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetAliasResponse;
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

public class LambdaComponentSpringTest extends CamelSpringTestSupport {

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
                File file = new File(classLoader.getResource("org/apache/camel/component/aws2/lambda/function/node/GetHelloWithName.zip").getFile());
                FileInputStream inputStream = new FileInputStream(file);
                exchange.getIn().setBody(inputStream);
            }
        });

        CreateFunctionResponse result = (CreateFunctionResponse)exchange.getMessage().getBody();
        assertEquals(result.functionName(), "GetHelloWithName");
        assertEquals(result.description(), "Hello with node.js on Lambda");
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
        GetFunctionResponse result = (GetFunctionResponse)exchange.getMessage().getBody();
        assertEquals(result.configuration().functionName(), "GetHelloWithName");
    }

    @Test
    public void lambdaListFunctionsTest() throws Exception {
        Exchange exchange = template.send("direct:listFunctions", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });

        ListFunctionsResponse result = (ListFunctionsResponse)exchange.getMessage().getBody();
        assertEquals(result.functions().size(), 1);
        assertEquals(result.functions().get(0).functionName(), "GetHelloWithName");
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
    public void lambdaCreateEventSourceMappingTest() throws Exception {
        Exchange exchange = template.send("direct:createEventSourceMapping", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.EVENT_SOURCE_ARN, "arn:aws:sqs:eu-central-1:643534317684:testqueue");
                exchange.getIn().setHeader(Lambda2Constants.EVENT_SOURCE_BATCH_SIZE, 100);
            }
        });
        assertMockEndpointsSatisfied();

        CreateEventSourceMappingResponse result = exchange.getMessage().getBody(CreateEventSourceMappingResponse.class);
        assertEquals(result.functionArn(), "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
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
        assertEquals(result.eventSourceMappings().get(0).functionArn(), "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
    }

    @Test
    public void lambdaListTagsTest() throws Exception {

        Exchange exchange = template.send("direct:listTags", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_ARN, "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
            }
        });
        assertMockEndpointsSatisfied();

        ListTagsResponse result = (ListTagsResponse)exchange.getMessage().getBody();
        assertEquals("lambda-tag", result.tags().get("test"));
    }

    @Test
    public void tagResourceTest() throws Exception {

        Exchange exchange = template.send("direct:tagResource", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> tags = new HashMap<>();
                tags.put("test", "added-tag");
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_ARN, "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_TAGS, tags);
            }
        });
        assertMockEndpointsSatisfied();

        TagResourceResponse result = (TagResourceResponse)exchange.getMessage().getBody();
        assertNotNull(result);
    }

    @Test
    public void untagResourceTest() throws Exception {

        Exchange exchange = template.send("direct:untagResource", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                List<String> tagKeys = new ArrayList<>();
                tagKeys.add("test");
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_ARN, "arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
                exchange.getIn().setHeader(Lambda2Constants.RESOURCE_TAG_KEYS, tagKeys);
            }
        });
        assertMockEndpointsSatisfied();

        UntagResourceResponse result = (UntagResourceResponse)exchange.getMessage().getBody();
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

        PublishVersionResponse result = (PublishVersionResponse)exchange.getMessage().getBody();
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

        ListVersionsByFunctionResponse result = (ListVersionsByFunctionResponse)exchange.getMessage().getBody();
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

        CreateAliasResponse result = (CreateAliasResponse)exchange.getMessage().getBody();
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

        DeleteAliasResponse result = (DeleteAliasResponse)exchange.getMessage().getBody();
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

        GetAliasResponse result = (GetAliasResponse)exchange.getMessage().getBody();
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

        ListAliasesResponse result = (ListAliasesResponse)exchange.getMessage().getBody();
        assertNotNull(result);
        assertEquals("an alias", result.aliases().get(0).description());
        assertEquals("alias", result.aliases().get(0).name());
        assertEquals("1", result.aliases().get(0).functionVersion());
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws2/lambda/LambdaComponentSpringTest-context.xml");
    }
}
