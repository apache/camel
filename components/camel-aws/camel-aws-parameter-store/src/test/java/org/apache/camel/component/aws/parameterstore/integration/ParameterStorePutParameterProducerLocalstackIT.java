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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import software.amazon.awssdk.services.ssm.model.PutParameterResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Flaky on GitHub Actions")
public class ParameterStorePutParameterProducerLocalstackIT extends AwsParameterStoreBaseTest {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void putParameterTest() {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:putParameter", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(ParameterStoreConstants.PARAMETER_NAME, "/test/param1");
                exchange.getIn().setHeader(ParameterStoreConstants.PARAMETER_TYPE, "String");
                exchange.getIn().setBody("TestValue1");
            }
        });

        PutParameterResponse result = (PutParameterResponse) exchange.getIn().getBody();
        assertNotNull(result);
        assertTrue(result.version() >= 1);
    }

    @Test
    public void putSecureStringParameterTest() {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:putParameter", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(ParameterStoreConstants.PARAMETER_NAME, "/test/secure/param1");
                exchange.getIn().setHeader(ParameterStoreConstants.PARAMETER_TYPE, "SecureString");
                exchange.getIn().setBody("SecretValue1");
            }
        });

        PutParameterResponse result = (PutParameterResponse) exchange.getIn().getBody();
        assertNotNull(result);
        assertTrue(result.version() >= 1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:putParameter")
                        .to("aws-parameter-store://test?operation=putParameter")
                        .to("mock:result");
            }
        };
    }
}
