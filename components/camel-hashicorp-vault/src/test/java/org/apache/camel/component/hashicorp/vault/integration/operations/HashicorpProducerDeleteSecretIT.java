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

import java.util.HashMap;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class HashicorpProducerDeleteSecretIT extends HashicorpVaultBase {

    @EndpointInject("mock:result-write")
    private MockEndpoint mockWrite;

    @EndpointInject("mock:result-delete")
    private MockEndpoint mockDelete;

    @Test
    public void createSecretTest() throws InterruptedException {

        mockWrite.expectedMessageCount(1);
        mockDelete.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                HashMap map = new HashMap();
                map.put("integer", "30");
                exchange.getIn().setBody(map);
            }
        });
        exchange = template.request("direct:deleteSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });

        MockEndpoint.assertIsSatisfied(context);
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

                from("direct:deleteSecret")
                        .toF("hashicorp-vault://secret?operation=deleteSecret&token=RAW(%s)&host=%s&port=%s&scheme=http&secretPath=test",
                                service.token(), service.host(), service.port())
                        .to("mock:result-delete");
            }
        };
    }
}
