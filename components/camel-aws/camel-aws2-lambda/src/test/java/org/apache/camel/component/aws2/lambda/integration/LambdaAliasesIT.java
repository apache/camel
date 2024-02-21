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

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.lambda.Lambda2Constants;
import org.apache.camel.component.aws2.lambda.Lambda2Operations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import software.amazon.awssdk.services.lambda.model.CreateAliasResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetAliasResponse;
import software.amazon.awssdk.services.lambda.model.ListAliasesResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on GitHub Actions")
public class LambdaAliasesIT extends Aws2LambdaBase {

    @Test
    public void createGetDeleteAndListAliasesShouldSucceed() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Lambda2Constants.RUNTIME, "nodejs16.x");
        headers.put(Lambda2Constants.HANDLER, "GetHelloWithName.handler");
        headers.put(Lambda2Constants.ROLE, "arn:aws:iam::643534317684:role/lambda-execution-role");
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream body
                = classLoader.getResourceAsStream("org/apache/camel/component/aws2/lambda/function/node/GetHelloWithName.zip");
        CreateFunctionResponse functionCreated
                = template.requestBodyAndHeaders("direct:createFunction", body, headers, CreateFunctionResponse.class);
        assertEquals("GetHelloWithName", functionCreated.functionName());

        headers = new HashMap<>();
        headers.put(Lambda2Constants.FUNCTION_ALIAS_NAME, "GetHelloWithNameAlias");
        headers.put(Lambda2Constants.FUNCTION_VERSION, "$LATEST");
        CreateAliasResponse aliasCreated
                = template.requestBodyAndHeaders("direct:createAlias", null, headers, CreateAliasResponse.class);
        assertEquals("GetHelloWithNameAlias", aliasCreated.name());
        assertEquals("$LATEST", aliasCreated.functionVersion());

        headers = new HashMap<>();
        headers.put(Lambda2Constants.FUNCTION_ALIAS_NAME, "GetHelloWithNameAlias");
        GetAliasResponse aliasGot = template.requestBodyAndHeaders("direct:getAlias", null, headers, GetAliasResponse.class);
        assertEquals("GetHelloWithNameAlias", aliasGot.name());
        assertEquals("$LATEST", aliasGot.functionVersion());

        ListAliasesResponse aliasesListed = template.requestBody("direct:listAliases", null, ListAliasesResponse.class);
        assertNotNull(aliasesListed.aliases());
        aliasesListed.aliases().stream().anyMatch(a -> "GetHelloWithNameAlias".equals(a.name()));

        headers = new HashMap<>();
        headers.put(Lambda2Constants.FUNCTION_ALIAS_NAME, "GetHelloWithNameAlias");
        template.requestBodyAndHeaders("direct:deleteAlias", null, headers);

        aliasesListed = template.requestBody("direct:listAliases", null, ListAliasesResponse.class);
        assertNotNull(aliasesListed.aliases());
        aliasesListed.aliases().stream().noneMatch(a -> "GetHelloWithNameAlias".equals(a.name()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String endpointUriFormat = "aws2-lambda://GetHelloWithName?operation=%s";

                from("direct:createFunction").toF(endpointUriFormat, Lambda2Operations.createFunction);
                from("direct:createAlias").toF(endpointUriFormat, Lambda2Operations.createAlias);
                from("direct:listAliases").toF(endpointUriFormat, Lambda2Operations.listAliases);
                from("direct:getAlias").toF(endpointUriFormat, Lambda2Operations.getAlias);
                from("direct:deleteAlias").toF(endpointUriFormat, Lambda2Operations.deleteAlias);
            }
        };
    }
}
