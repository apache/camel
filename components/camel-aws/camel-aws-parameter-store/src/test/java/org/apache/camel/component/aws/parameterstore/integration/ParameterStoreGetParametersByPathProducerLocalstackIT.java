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
package org.apache.camel.component.aws.parameterstore.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.parameterstore.ParameterStoreConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Flaky on GitHub Actions")
public class ParameterStoreGetParametersByPathProducerLocalstackIT extends AwsParameterStoreBaseTest {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @BeforeAll
    public static void setupParameters() {
        SsmClient client = getSsmClient();
        client.putParameter(PutParameterRequest.builder()
                .name("/myapp/config/db-host")
                .value("localhost")
                .type(ParameterType.STRING)
                .build());
        client.putParameter(PutParameterRequest.builder()
                .name("/myapp/config/db-port")
                .value("5432")
                .type(ParameterType.STRING)
                .build());
        client.putParameter(PutParameterRequest.builder()
                .name("/myapp/config/db-user")
                .value("admin")
                .type(ParameterType.STRING)
                .build());
    }

    @Test
    public void getParametersByPathTest() {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getParametersByPath", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(ParameterStoreConstants.PARAMETER_PATH, "/myapp/config");
                exchange.getIn().setHeader(ParameterStoreConstants.RECURSIVE, true);
            }
        });

        GetParametersByPathResponse result = (GetParametersByPathResponse) exchange.getIn().getBody();
        assertNotNull(result);
        assertFalse(result.parameters().isEmpty());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:getParametersByPath")
                        .to("aws-parameter-store://test?operation=getParametersByPath")
                        .to("mock:result");
            }
        };
    }
}
