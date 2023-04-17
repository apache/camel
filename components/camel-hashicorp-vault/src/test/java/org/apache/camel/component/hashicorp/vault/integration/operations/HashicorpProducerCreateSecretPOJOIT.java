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

package org.apache.camel.component.hashicorp.vault.integration.operations;

import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hashicorp.vault.HashicorpVaultConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HashicorpProducerCreateSecretPOJOIT extends HashicorpVaultBase {

    @EndpointInject("mock:result-write")
    private MockEndpoint mockWrite;

    @EndpointInject("mock:result-read")
    private MockEndpoint mockRead;

    @Test
    public void createSecretTest() throws InterruptedException {

        mockWrite.expectedMessageCount(1);
        mockRead.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                Secrets sec = new Secrets();
                sec.username = "admin";
                sec.password = "password";
                exchange.getIn().setBody(sec);
            }
        });
        exchange = template.request("direct:readSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(HashicorpVaultConstants.SECRET_PATH, "test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        Exchange ret = mockRead.getExchanges().get(0);
        assertNotNull(ret);
        assertEquals("admin", ((Map) ret.getMessage().getBody(Map.class).get("data")).get("username"));
        assertEquals("password", ((Map) ret.getMessage().getBody(Map.class).get("data")).get("password"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createSecret")
                        .toF("hashicorp-vault://secret?operation=createSecret&token=RAW(%s)&host=%s&port=%s&scheme=http&secretPath=test",
                                service.token(), service.host(), service.port())
                        .to("mock:result-write");
                from("direct:readSecret")
                        .toF("hashicorp-vault://secret?operation=getSecret&token=RAW(%s)&host=%s&port=%s&scheme=http",
                                service.token(), service.host(), service.port())
                        .to("mock:result-read");
            }
        };
    }

    class Secrets {

        String username;
        String password;

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }
}
