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
package org.apache.camel.component.aws.securityhub;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.securityhub.SecurityHubClient;
import software.amazon.awssdk.services.securityhub.model.GetFindingAggregatorRequest;
import software.amazon.awssdk.services.securityhub.model.GetFindingAggregatorResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SecurityHubGetFindingAggregatorTest extends CamelTestSupport {

    private static final String AGGREGATOR_ARN
            = "arn:aws:securityhub:eu-west-1:123456789012:finding-aggregator/abcd";

    @BindToRegistry("securityHubClient")
    private final SecurityHubClient securityHubClient = mock(SecurityHubClient.class);

    @Test
    public void getFindingAggregatorIsSupportedAndUsesTheArnHeader() throws Exception {
        when(securityHubClient.getFindingAggregator(any(GetFindingAggregatorRequest.class)))
                .thenReturn(GetFindingAggregatorResponse.builder().findingAggregatorArn(AGGREGATOR_ARN).build());

        // Before the fix this operation fell through to "Unsupported operation".
        template.send("direct:start",
                exchange -> exchange.getIn().setHeader(SecurityHubConstants.FINDING_AGGREGATOR_ARN, AGGREGATOR_ARN));

        ArgumentCaptor<GetFindingAggregatorRequest> captor
                = ArgumentCaptor.forClass(GetFindingAggregatorRequest.class);
        verify(securityHubClient).getFindingAggregator(captor.capture());
        assertEquals(AGGREGATOR_ARN, captor.getValue().findingAggregatorArn());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("aws-security-hub://test?securityHubClient=#securityHubClient&operation=getFindingAggregator");
            }
        };
    }
}
