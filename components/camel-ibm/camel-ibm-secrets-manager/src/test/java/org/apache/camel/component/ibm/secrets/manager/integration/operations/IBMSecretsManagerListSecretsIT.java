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

import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.model.SecretMetadataPaginatedCollection;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.secrets.manager.IBMSecretsManagerConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Must be manually tested. Provide your own accessKey and secretKey using -Dsecrets-manager and -Dcamel.ibm.sm.serviceurl
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.sm.token", matches = ".*",
                                 disabledReason = "Secrets Manager Token not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.sm.serviceurl", matches = ".*",
                                 disabledReason = "Secrets Manager Service URL not provided")
})
public class IBMSecretsManagerListSecretsIT extends CamelTestSupport {

    @EndpointInject("mock:result-write")
    private MockEndpoint mockWrite;

    @EndpointInject("mock:result-list")
    private MockEndpoint mockList;

    @EndpointInject("mock:result-delete")
    private MockEndpoint mockDelete;

    @Test
    public void createSecretTest() throws InterruptedException {
        mockWrite.expectedMessageCount(1);
        mockList.expectedMessageCount(1);
        mockDelete.expectedMessageCount(1);
        Exchange createdSec = template.request("direct:createSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("test");

                exchange.getMessage().setHeader(IBMSecretsManagerConstants.SECRET_NAME, "secret1");
            }
        });
        Exchange listSec = template.request("direct:listSecrets", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(IBMSecretsManagerConstants.SECRET_ID, createdSec.getMessage().getBody());
            }
        });
        template.request("direct:deleteSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(IBMSecretsManagerConstants.SECRET_ID, createdSec.getMessage().getBody());
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        Exchange ret = mockList.getExchanges().get(0);
        assertNotNull(ret);
        SecretMetadataPaginatedCollection collection = ret.getMessage().getBody(SecretMetadataPaginatedCollection.class);
        assertEquals("secret1", collection.getSecrets().get(0).getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createSecret")
                        .toF("ibm-secrets-manager://secret?operation=createArbitrarySecret&token=RAW(%s)&serviceUrl=%s",
                                System.getProperty("camel.ibm.sm.token"), System.getProperty("camel.ibm.sm.serviceurl"))
                        .to("mock:result-write");
                from("direct:listSecrets")
                        .toF("ibm-secrets-manager://secret?operation=listSecrets&token=RAW(%s)&serviceUrl=%s",
                                System.getProperty("camel.ibm.sm.token"), System.getProperty("camel.ibm.sm.serviceurl"))
                        .to("mock:result-list");
                from("direct:deleteSecret")
                        .toF("ibm-secrets-manager://secret?operation=deleteSecret&token=RAW(%s)&serviceUrl=%s",
                                System.getProperty("camel.ibm.sm.token"), System.getProperty("camel.ibm.sm.serviceurl"))
                        .to("mock:result-delete");
            }
        };
    }
}
