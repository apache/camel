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
package org.apache.camel.component.aws2.kms;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.ListKeysRequest;
import software.amazon.awssdk.services.kms.model.ListKeysResponse;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class KMSProducerTest extends CamelTestSupport {

    @BindToRegistry("amazonKmsClient")
    AmazonKMSClientMock clientMock = new AmazonKMSClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void kmsListBrokersTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listKeys", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, KMS2Operations.listKeys);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListKeysResponse resultGet = (ListKeysResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.keys().size());
        assertEquals("keyId", resultGet.keys().get(0).keyId());
    }

    @Test
    public void kmsListKeysPojoTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listKeysPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, KMS2Operations.listKeys);
                exchange.getIn().setBody(ListKeysRequest.builder().limit(10).build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListKeysResponse resultGet = (ListKeysResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.keys().size());
        assertEquals("keyId", resultGet.keys().get(0).keyId());
    }

    @Test
    public void kmsCreateKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createKey", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, KMS2Operations.createKey);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateKeyResponse resultGet = (CreateKeyResponse) exchange.getIn().getBody();
        assertEquals("test", resultGet.keyMetadata().keyId());
        assertEquals(true, resultGet.keyMetadata().enabled());
    }

    @Test
    public void kmsDisableKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        template.request("direct:disableKey", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, KMS2Operations.disableKey);
                exchange.getIn().setHeader(KMS2Constants.KEY_ID, "test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

    }

    @Test
    public void kmsEnableKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        template.request("direct:enableKey", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, KMS2Operations.enableKey);
                exchange.getIn().setHeader(KMS2Constants.KEY_ID, "test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

    }

    @Test
    public void kmsScheduleKeyDeletionTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:scheduleDelete", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, KMS2Operations.scheduleKeyDeletion);
                exchange.getIn().setHeader(KMS2Constants.KEY_ID, "test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ScheduleKeyDeletionResponse resultGet = (ScheduleKeyDeletionResponse) exchange.getIn().getBody();
        assertEquals("test", resultGet.keyId());
    }

    @Test
    public void kmsDescribeKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeKey", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(KMS2Constants.OPERATION, KMS2Operations.describeKey);
                exchange.getIn().setHeader(KMS2Constants.KEY_ID, "test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeKeyResponse resultGet = exchange.getIn().getBody(DescribeKeyResponse.class);
        assertEquals("test", resultGet.keyMetadata().keyId());
        assertEquals("MyCamelKey", resultGet.keyMetadata().description());
        assertFalse(resultGet.keyMetadata().enabled());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:listKeys").to("aws2-kms://test?kmsClient=#amazonKmsClient&operation=listKeys").to("mock:result");
                from("direct:listKeysPojo").to("aws2-kms://test?kmsClient=#amazonKmsClient&operation=listKeys&pojoRequest=true")
                        .to("mock:result");
                from("direct:createKey").to("aws2-kms://test?kmsClient=#amazonKmsClient&operation=createKey").to("mock:result");
                from("direct:disableKey").to("aws2-kms://test?kmsClient=#amazonKmsClient&operation=disableKey")
                        .to("mock:result");
                from("direct:enableKey").to("aws2-kms://test?kmsClient=#amazonKmsClient&operation=enableKey").to("mock:result");
                from("direct:scheduleDelete").to("aws2-kms://test?kmsClient=#amazonKmsClient&operation=scheduleKeyDeletion")
                        .to("mock:result");
                from("direct:describeKey").to("aws2-kms://test?kmsClient=#amazonKmsClient&operation=describeKey")
                        .to("mock:result");
            }
        };
    }
}
