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
package org.apache.camel.component.azure.key.vault.integration.operations;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "vaultName", matches = ".*",
        disabledReason = "Make sure to supply azure key vault Vault Name, e.g:  mvn verify -DvaultName=string")
@EnabledIfSystemProperty(named = "clientId", matches = ".*",
        disabledReason = "Make sure to supply azure key vault Client Id, e.g:  mvn verify -DclientId=string")
@EnabledIfSystemProperty(named = "clientSecret", matches = ".*",
        disabledReason = "Make sure to supply azure key vault Client Secret, e.g:  mvn verify -DclientSecret=string")
@EnabledIfSystemProperty(named = "tenantId", matches = ".*",
        disabledReason = "Make sure to supply azure key vault Tenant Id, e.g:  mvn verify -DtenantId=string")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KeyVaultOperationsTest extends CamelTestSupport {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Test");
            }
        });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").startupOrder(1)
                        .to("azure-key-vault://{{vaultName}}?clientId=RAW({{clientId}})&clientSecret=RAW({{clientSecret}})&tenantId=RAW({{tenantId}})")
                        .to("mock:result");

            }
        };
    }
}
