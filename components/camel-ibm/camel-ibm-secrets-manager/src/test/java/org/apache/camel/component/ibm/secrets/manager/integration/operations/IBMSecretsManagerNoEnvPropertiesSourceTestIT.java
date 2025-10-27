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
package org.apache.camel.component.ibm.secrets.manager.integration.operations;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

// Must be manually tested. Provide your own accessKey and secretKey using -Dsecrets-manager and -Dcamel.ibm.sm.serviceurl
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.sm.token", matches = ".*",
                                 disabledReason = "Secrets Manager Token not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.sm.serviceurl", matches = ".*",
                                 disabledReason = "Secrets Manager Service URL not provided")
})
public class IBMSecretsManagerNoEnvPropertiesSourceTestIT extends CamelTestSupport {

    @Test
    public void testFunction() throws Exception {
        context.getVaultConfiguration().ibmSecretsManager().setToken(System.getProperty("camel.ibm.sm.token"));
        context.getVaultConfiguration().ibmSecretsManager().setServiceUrl(System.getProperty("camel.ibm.sm.serviceurl"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{ibm:default:authsecdb#username}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFunctionWithVersion() throws Exception {
        context.getVaultConfiguration().ibmSecretsManager().setToken(System.getProperty("camel.ibm.sm.token"));
        context.getVaultConfiguration().ibmSecretsManager().setServiceUrl(System.getProperty("camel.ibm.sm.serviceurl"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{ibm:default:authsecdb#username@current}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFunctionWithExistentVersion() throws Exception {
        context.getVaultConfiguration().ibmSecretsManager().setToken(System.getProperty("camel.ibm.sm.token"));
        context.getVaultConfiguration().ibmSecretsManager().setServiceUrl(System.getProperty("camel.ibm.sm.serviceurl"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{ibm:default:authsecdb#username@00221dc6-1911-c29e-fd7a-c4c5d88ce13f}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFunctionWithArbitrarySecret() throws Exception {
        context.getVaultConfiguration().ibmSecretsManager().setToken(System.getProperty("camel.ibm.sm.token"));
        context.getVaultConfiguration().ibmSecretsManager().setServiceUrl(System.getProperty("camel.ibm.sm.serviceurl"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{ibm:default:pippo}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("sese");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFunctionWithArbitrarySecretNonExistentAndDefault() throws Exception {
        context.getVaultConfiguration().ibmSecretsManager().setToken(System.getProperty("camel.ibm.sm.token"));
        context.getVaultConfiguration().ibmSecretsManager().setServiceUrl(System.getProperty("camel.ibm.sm.serviceurl"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{ibm:default:secsecret:sese}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("sese");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }
}
