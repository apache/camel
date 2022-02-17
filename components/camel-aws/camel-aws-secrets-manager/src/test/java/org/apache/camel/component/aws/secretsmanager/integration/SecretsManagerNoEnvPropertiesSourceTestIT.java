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
package org.apache.camel.component.aws.secretsmanager.integration;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.apache.camel.util.CollectionHelper.propertiesOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Must be manually tested. Provide your own accessKey and secretKey using -Dcamel.aws.vault.access.key, -Dcamel.aws.vault.secret.key and -Dcamel.aws.vault.region
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.aws.vault.access.key", matches = ".*",
                                 disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "camel.aws.vault.secret.key", matches = ".*",
                                 disabledReason = "Secret key not provided"),
        @EnabledIfSystemProperty(named = "camel.aws.vault.region", matches = ".*", disabledReason = "Region not provided"),
})
public class SecretsManagerNoEnvPropertiesSourceTestIT extends CamelTestSupport {

    @Test
    public void testFunction() throws Exception {
        context.getPropertiesComponent().setOverrideProperties(
                propertiesOf("camel.aws.vault.access.key", System.getProperty("camel.aws.vault.access.key"),
                        "camel.aws.vault.secret.key", System.getProperty("camel.aws.vault.secret.key"),
                        "camel.aws.vault.region", System.getProperty("camel.aws.vault.region")));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").setBody(simple("{{aws:hello}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSecretNotFoundFunction() throws Exception {
        context.getPropertiesComponent().setOverrideProperties(
                propertiesOf("camel.aws.vault.access.key", System.getProperty("camel.aws.vault.access.key"),
                        "camel.aws.vault.secret.key", System.getProperty("camel.aws.vault.secret.key"),
                        "camel.aws.vault.region", System.getProperty("camel.aws.vault.region")));
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start").setBody(simple("{{aws:testExample}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

            template.sendBody("direct:start", "Hello World");

            assertMockEndpointsSatisfied();
        });
    }

}
