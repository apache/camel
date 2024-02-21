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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.secretsmanager.SecretsManagerConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.UpdateSecretResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on GitHub Actions")
public class SecretsManagerUpdateSecretProducerLocalstackIT extends AwsSecretsManagerBaseTest {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void createSecretTest() {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(SecretsManagerConstants.SECRET_NAME, "TestSecret4");
                exchange.getIn().setBody("Body");
            }
        });

        CreateSecretResponse resultGet = (CreateSecretResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);

        exchange = template.request("direct:updateSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(SecretsManagerConstants.SECRET_ID, resultGet.arn());
                exchange.getIn().setBody("Binary Body");
            }
        });
        Assertions.assertNotNull(exchange);

        UpdateSecretResponse resultUpdate = (UpdateSecretResponse) exchange.getIn().getBody();
        assertTrue(resultUpdate.sdkHttpResponse().isSuccessful());
        assertEquals("TestSecret4", resultUpdate.name());

        exchange = template.request("direct:getSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(SecretsManagerConstants.SECRET_ID, resultGet.arn());
            }
        });
        Assertions.assertNotNull(exchange);

        String secret = exchange.getIn().getBody(String.class);
        assertEquals("Binary Body", secret);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createSecret")
                        .to("aws-secrets-manager://test?operation=createSecret");

                from("direct:updateSecret")
                        .to("aws-secrets-manager://test?operation=updateSecret&binaryPayload=true");

                from("direct:getSecret")
                        .to("aws-secrets-manager://test?operation=getSecret&binaryPayload=true")
                        .to("mock:result");
            }
        };
    }
}
