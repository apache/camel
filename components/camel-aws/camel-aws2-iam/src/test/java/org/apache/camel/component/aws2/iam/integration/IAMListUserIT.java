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
package org.apache.camel.component.aws2.iam.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.iam.IAM2Constants;
import org.apache.camel.component.aws2.iam.IAM2Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.iam.model.ListUsersResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IAMListUserIT extends Aws2IAMBase {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void iamCreateUserTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createUser", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.createUser);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
            }
        });
        exchange = template.request("direct:listUsers", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.listUsers);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListUsersResponse resultGet = (ListUsersResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.users().size());
        assertEquals("test", resultGet.users().get(0).userName());

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createUser").to("aws2-iam://test?operation=createUser");
                from("direct:listUsers").to("aws2-iam://test?operation=listUsers").to("mock:result");

            }
        };
    }
}
