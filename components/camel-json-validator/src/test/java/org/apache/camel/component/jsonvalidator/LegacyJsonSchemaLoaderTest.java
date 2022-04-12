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
package org.apache.camel.component.jsonvalidator;

import java.io.*;
import java.net.*;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Applications that rely on an instance of the legacy {@link JsonSchemaLoader} should continue to work.
 */
public class LegacyJsonSchemaLoaderTest extends CamelTestSupport {

    @EndpointInject("mock:end")
    protected MockEndpoint endpoint;

    @Test
    public void testValidMessage() throws Exception {
        endpoint.expectedMessageCount(1);

        template.sendBody("direct:start",
                "{ \"customer\": \"Donald \\\"Duck\\\" Dunn\", \"orderItems\": [{ \"product\": \"bass guitar\", \"quantity\": 1 }] }");

        MockEndpoint.assertIsSatisfied(endpoint);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        JsonValidatorEndpoint endpoint = context.getEndpoint(
                "json-validator:org/apache/camel/component/jsonvalidator/schema.json", JsonValidatorEndpoint.class);
        endpoint.setSchemaLoader(new JsonSchemaLoader() {
            @Override
            public JsonSchema createSchema(CamelContext camelContext, InputStream inputStream) {
                // ignore the requested schema and always return Order.json schema ... the validation will only
                // succeed if it's done with this schema
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);
                return factory.getSchema(URI.create("classpath:org/apache/camel/component/jsonvalidator/Order.json"));
            }
        });
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("json-validator:org/apache/camel/component/jsonvalidator/schema.json")
                        .to("mock:end");
            }
        };
    }
}
