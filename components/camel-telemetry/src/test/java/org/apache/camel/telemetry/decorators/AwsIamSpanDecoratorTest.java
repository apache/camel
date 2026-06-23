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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.telemetry.mock.MockSpanAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AwsIamSpanDecoratorTest {

    @Test
    public void testPre() {
        String operation = "createGroup";
        String groupName = "engineers";
        String roleName = "deployer";
        String policyName = "ReadOnlyAccess";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-iam:default");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(exchange.getExchangeId()).thenReturn("exchange-1");
        Mockito.when(message.getHeader(AwsIamSpanDecorator.OPERATION, String.class)).thenReturn(operation);
        Mockito.when(message.getHeader(AwsIamSpanDecorator.GROUP_NAME, String.class)).thenReturn(groupName);
        Mockito.when(message.getHeader(AwsIamSpanDecorator.ROLE_NAME, String.class)).thenReturn(roleName);
        Mockito.when(message.getHeader(AwsIamSpanDecorator.POLICY_NAME, String.class)).thenReturn(policyName);

        AbstractSpanDecorator decorator = new AwsIamSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(operation, span.tags().get(AwsIamSpanDecorator.IAM_OPERATION));
        assertEquals(groupName, span.tags().get(AwsIamSpanDecorator.IAM_GROUP_NAME));
        assertEquals(roleName, span.tags().get(AwsIamSpanDecorator.IAM_ROLE_NAME));
        assertEquals(policyName, span.tags().get(AwsIamSpanDecorator.IAM_POLICY_NAME));
    }

}
