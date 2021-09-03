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
package org.apache.camel.component.aws2.sts;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.GetFederationTokenResponse;
import software.amazon.awssdk.services.sts.model.GetSessionTokenResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class STS2ProducerTest extends CamelTestSupport {

    @BindToRegistry("amazonStsClient")
    AmazonSTSClientMock clientMock = new AmazonSTSClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void stsAssumeRoleTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:assumeRole", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(STS2Constants.OPERATION, STS2Operations.assumeRole);
                exchange.getIn().setHeader(STS2Constants.ROLE_ARN, "arn");
                exchange.getIn().setHeader(STS2Constants.ROLE_SESSION_NAME, "sessionarn");
            }
        });

        assertMockEndpointsSatisfied();

        AssumeRoleResponse resultGet = (AssumeRoleResponse) exchange.getIn().getBody();
        assertEquals("arn", resultGet.assumedRoleUser().arn());
    }

    @Test
    public void stsGetSessionTokenTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getSessionToken", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(STS2Constants.OPERATION, STS2Operations.getSessionToken);
            }
        });

        assertMockEndpointsSatisfied();

        GetSessionTokenResponse resultGet = (GetSessionTokenResponse) exchange.getIn().getBody();
        assertEquals("xxx", resultGet.credentials().accessKeyId());
    }

    @Test
    public void stsGetFederationTokenTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getFederationToken", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(STS2Constants.OPERATION, STS2Operations.getFederationToken);
                exchange.getIn().setHeader(STS2Constants.FEDERATED_NAME, "federation-account");
            }
        });

        assertMockEndpointsSatisfied();

        GetFederationTokenResponse resultGet = (GetFederationTokenResponse) exchange.getIn().getBody();
        assertEquals("xxx", resultGet.credentials().accessKeyId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:assumeRole").to("aws2-sts://test?stsClient=#amazonStsClient&operation=assumeRole")
                        .to("mock:result");
                from("direct:getSessionToken").to("aws2-sts://test?stsClient=#amazonStsClient&operation=getSessionToken")
                        .to("mock:result");
                from("direct:getFederationToken").to("aws2-sts://test?stsClient=#amazonStsClient&operation=getFederationToken")
                        .to("mock:result");
            }
        };
    }
}
