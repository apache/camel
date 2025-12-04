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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.secretsmanager.SecretsManagerConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;

public class SecretsManagerBatchGetSecretProducerLocalstackIT extends AwsSecretsManagerBaseTest {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void createSecretTest() {

        mock.expectedMessageCount(1);
        Exchange sec1 = template.request("direct:createSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(SecretsManagerConstants.SECRET_NAME, "TestSecret2");
                exchange.getIn().setBody("Secret 2");
            }
        });

        Exchange sec2 = template.request("direct:createSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(SecretsManagerConstants.SECRET_NAME, "TestSecret1");
                exchange.getIn().setBody("Secret 1");
            }
        });

        CreateSecretResponse resultGet1 = (CreateSecretResponse) sec1.getIn().getBody();
        assertNotNull(resultGet1);

        CreateSecretResponse resultGet2 = (CreateSecretResponse) sec2.getIn().getBody();
        assertNotNull(resultGet2);

        Exchange exchangeFinal = template.request("direct:batchGetSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                String secretsIds = resultGet1.arn() + "," + resultGet2.arn();
                exchange.getIn().setHeader(SecretsManagerConstants.SECRET_IDS, secretsIds);
            }
        });

        List<String> secrets = exchangeFinal.getIn().getBody(List.class);
        assertEquals(2, secrets.size());
        assertEquals("Secret 2", secrets.get(0));
        assertEquals("Secret 1", secrets.get(1));
        assertEquals(
                2,
                exchangeFinal
                        .getMessage()
                        .getHeader(SecretsManagerConstants.SECRET_VERSION_IDS, List.class)
                        .size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createSecret").to("aws-secrets-manager://test?operation=createSecret");

                from("direct:batchGetSecret")
                        .to("aws-secrets-manager://test?operation=batchGetSecret")
                        .to("mock:result");
            }
        };
    }
}
