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
package org.apache.camel.component.hashicorp.vault.integration;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class HashicorpVaultPropertiesSourceTestIT extends CamelTestSupport {

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN_ENV", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE_ENV", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST_ENV", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT_ENV", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME_ENV", matches = ".*")
    @Test
    public void testFunctio() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{hashicorp:hello}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("{id=21}");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN_ENV", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE_ENV", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST_ENV", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT_ENV", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME_ENV", matches = ".*")
    @Test
    public void testFunctionWithField() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{hashicorp:hello/id}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("21");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }
}
