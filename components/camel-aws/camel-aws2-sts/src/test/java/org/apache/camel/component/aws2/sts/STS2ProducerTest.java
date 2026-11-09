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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(STS2Constants.OPERATION, STS2Operations.assumeRole);
                exchange.getIn().setHeader(STS2Constants.ROLE_ARN, "arn");
                exchange.getIn().setHeader(STS2Constants.ROLE_SESSION_NAME, "sessionarn");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        AssumeRoleResponse resultGet = (AssumeRoleResponse) exchange.getIn().getBody();
        assertEquals("arn", resultGet.assumedRoleUser().arn());
    }

    @Test
    public void stsGetSessionTokenTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getSessionToken", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(STS2Constants.OPERATION, STS2Operations.getSessionToken);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        GetSessionTokenResponse resultGet = (GetSessionTokenResponse) exchange.getIn().getBody();
        assertEquals("xxx", resultGet.credentials().accessKeyId());
    }

    @Test
    public void stsGetFederationTokenTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getFederationToken", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(STS2Constants.OPERATION, STS2Operations.getFederationToken);
                exchange.getIn().setHeader(STS2Constants.FEDERATED_NAME, "federation-account");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        GetFederationTokenResponse resultGet = (GetFederationTokenResponse) exchange.getIn().getBody();
        assertEquals("xxx", resultGet.credentials().accessKeyId());
    }

    @Test
    void stsGetFederationTokenWithoutFederatedNameReportsTheCorrectOperation() {
        assertThatThrownBy(() -> template.requestBody("direct:getFederationToken", "body"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Federated name needs to be specified for getFederationToken operation");
    }

    @Test
    void assumeRoleDoesNotCopyTheSecretKeyAndSessionTokenIntoHeaders() {
        Exchange exchange = template.request("direct:assumeRole", e -> {
            e.getIn().setHeader(STS2Constants.ROLE_ARN, "arn");
            e.getIn().setHeader(STS2Constants.ROLE_SESSION_NAME, "sessionarn");
        });

        assertCredentialHeaders(exchange);
        assertThat(exchange.getMessage().getHeader(STS2Constants.ASSUMED_ROLE_ARN)).isEqualTo("arn");
        AssumeRoleResponse body = exchange.getMessage().getBody(AssumeRoleResponse.class);
        assertThat(body.credentials().secretAccessKey()).isEqualTo("yyy");
        assertThat(body.credentials().sessionToken()).isEqualTo("test");
    }

    @Test
    void getSessionTokenDoesNotCopyTheSecretKeyAndSessionTokenIntoHeaders() {
        Exchange exchange = template.request("direct:getSessionToken", e -> {
        });

        assertCredentialHeaders(exchange);
        GetSessionTokenResponse body = exchange.getMessage().getBody(GetSessionTokenResponse.class);
        assertThat(body.credentials().secretAccessKey()).isEqualTo("yyy");
        assertThat(body.credentials().sessionToken()).isEqualTo("test");
    }

    @Test
    void getFederationTokenDoesNotCopyTheSecretKeyAndSessionTokenIntoHeaders() {
        Exchange exchange = template.request("direct:getFederationToken",
                e -> e.getIn().setHeader(STS2Constants.FEDERATED_NAME, "federation-account"));

        assertCredentialHeaders(exchange);
        GetFederationTokenResponse body = exchange.getMessage().getBody(GetFederationTokenResponse.class);
        assertThat(body.credentials().secretAccessKey()).isEqualTo("yyy");
        assertThat(body.credentials().sessionToken()).isEqualTo("test");
    }

    @Test
    void theSecretKeyAndSessionTokenCanBeTakenFromTheBody() {
        Exchange exchange = template.request("direct:assumeRoleCredentialsFromBody", e -> {
            e.getIn().setHeader(STS2Constants.ROLE_ARN, "arn");
            e.getIn().setHeader(STS2Constants.ROLE_SESSION_NAME, "sessionarn");
        });

        assertThat(exchange.getMessage().getBody(String.class)).isEqualTo("yyy test");
    }

    private static void assertCredentialHeaders(Exchange exchange) {
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeaders())
                .containsEntry(STS2Constants.ACCESS_KEY_ID, "xxx")
                .containsEntry(STS2Constants.EXPIRATION, AmazonSTSClientMock.EXPIRATION)
                .doesNotContainKeys("CamelAwsStsSecretKey", "CamelAwsStsSessionToken");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:assumeRole").to("aws2-sts://test?stsClient=#amazonStsClient&operation=assumeRole")
                        .to("mock:result");
                from("direct:getSessionToken").to("aws2-sts://test?stsClient=#amazonStsClient&operation=getSessionToken")
                        .to("mock:result");
                from("direct:getFederationToken").to("aws2-sts://test?stsClient=#amazonStsClient&operation=getFederationToken")
                        .to("mock:result");
                from("direct:assumeRoleCredentialsFromBody")
                        .to("aws2-sts://test?stsClient=#amazonStsClient&operation=assumeRole")
                        .setBody(simple("${body.credentials().secretAccessKey()} ${body.credentials().sessionToken()}"));
            }
        };
    }
}
