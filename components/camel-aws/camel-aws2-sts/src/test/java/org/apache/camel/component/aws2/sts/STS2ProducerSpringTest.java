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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.GetFederationTokenResponse;
import software.amazon.awssdk.services.sts.model.GetSessionTokenResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class STS2ProducerSpringTest extends CamelSpringTestSupport {

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

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws2/sts/STSComponentSpringTest-context.xml");
    }
}
