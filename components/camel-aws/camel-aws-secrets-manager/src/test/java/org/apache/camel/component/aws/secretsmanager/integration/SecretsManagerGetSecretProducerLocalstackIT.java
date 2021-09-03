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
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SecretsManagerGetSecretProducerLocalstackIT extends AwsSecretsManagerBaseTest {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void createSecretTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createSecret", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SecretsManagerConstants.SECRET_NAME, "TestSecret4");
                exchange.getIn().setBody("Body");
            }
        });

        CreateSecretResponse resultGet = (CreateSecretResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);

        exchange = template.request("direct:getSecret", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SecretsManagerConstants.SECRET_ID, resultGet.arn());
            }
        });

        String secret = exchange.getIn().getBody(String.class);
        assertEquals("Body", secret);

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:createSecret")
                        .to("aws-secrets-manager://test?operation=createSecret");

                from("direct:getSecret")
                        .to("aws-secrets-manager://test?operation=getSecret")
                        .to("mock:result");
            }
        };
    }
}
