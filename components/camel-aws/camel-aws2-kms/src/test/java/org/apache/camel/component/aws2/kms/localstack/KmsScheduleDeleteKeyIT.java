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
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.KeyState;

import static org.junit.Assert.assertEquals;

public class KmsScheduleDeleteKeyIT extends Aws2KmsBase {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        Exchange ex = template.send("direct:createKey", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, "createKey");
            }
        });

        String keyId = ex.getMessage().getBody(CreateKeyResponse.class).keyMetadata().keyId();

        template.send("direct:scheduleDelete", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, "scheduleKeyDeletion");
                exchange.getIn().setHeader(KMS2Constants.KEY_ID, keyId);
            }
        });

        template.send("direct:describeKey", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, "describeKey");
                exchange.getIn().setHeader(KMS2Constants.KEY_ID, keyId);
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(1, result.getExchanges().size());
        assertEquals(KeyState.PENDING_DELETION,
                result.getExchanges().get(0).getIn().getBody(DescribeKeyResponse.class).keyMetadata().keyState());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint
                        = "aws2-kms://default?operation=createKey";
                String describeKey
                        = "aws2-kms://default?operation=describeKey";
                String scheduleDelete
                        = "aws2-kms://default?operation=describeKey";
                from("direct:createKey").to(awsEndpoint);
                from("direct:scheduleDelete").to(scheduleDelete);
                from("direct:describeKey").to(describeKey).to("mock:result");
            }
        };
    }
}
