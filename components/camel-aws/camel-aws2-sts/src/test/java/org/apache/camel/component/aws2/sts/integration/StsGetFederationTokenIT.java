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
package org.apache.camel.component.aws2.sts.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sts.STS2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sts.model.GetFederationTokenResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class StsGetFederationTokenIT extends Aws2StsBase {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:getFederationToken", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(STS2Constants.OPERATION, "getFederationToken");
                exchange.getIn().setHeader(STS2Constants.FEDERATED_NAME, "test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(1, result.getExchanges().size());
        assertNotNull(
                result.getExchanges().get(0).getIn().getBody(GetFederationTokenResponse.class).credentials().accessKeyId());
        assertEquals("000000000000:test",
                result.getExchanges().get(0).getIn().getBody(GetFederationTokenResponse.class).federatedUser()
                        .federatedUserId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint
                        = "aws2-sts://default?operation=getFederationToken";
                from("direct:getFederationToken").to(awsEndpoint).to("mock:result");
            }
        };
    }
}
