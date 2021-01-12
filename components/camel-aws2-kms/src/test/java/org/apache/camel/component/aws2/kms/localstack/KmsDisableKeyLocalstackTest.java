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
package org.apache.camel.component.aws2.kms.localstack;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.kms.KMS2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.ListKeysResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KmsDisableKeyLocalstackTest extends Aws2KmsBaseTest {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        Exchange ex = template.send("direct:createKey", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, "createKey");
            }
        });

        String keyId = ex.getMessage().getBody(CreateKeyResponse.class).keyMetadata().keyId();

        template.send("direct:disableKey", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, "disableKey");
                exchange.getIn().setHeader(KMS2Constants.KEY_ID, keyId);
            }
        });

        template.send("direct:listKeys", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, "listKeys");
            }
        });

        assertMockEndpointsSatisfied();
        assertEquals(1, result.getExchanges().size());
        assertTrue(result.getExchanges().get(0).getIn().getBody(ListKeysResponse.class).hasKeys());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String awsEndpoint
                        = "aws2-kms://default?operation=createKey";
                String disableKey = "aws2-kms://default?operation=disableKey";
                String listKeys = "aws2-kms://default?operation=listKeys";
                from("direct:createKey").to(awsEndpoint);
                from("direct:disableKey").to(disableKey);
                from("direct:listKeys").to(listKeys).to("mock:result");
            }
        };
    }
}
