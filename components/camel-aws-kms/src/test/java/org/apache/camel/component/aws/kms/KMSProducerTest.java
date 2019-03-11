/**
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
package org.apache.camel.component.aws.kms;

import com.amazonaws.services.kms.model.CreateKeyResult;
import com.amazonaws.services.kms.model.DescribeKeyResult;
import com.amazonaws.services.kms.model.ListKeysResult;
import com.amazonaws.services.kms.model.ScheduleKeyDeletionResult;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class KMSProducerTest extends CamelTestSupport {
    
    @EndpointInject(uri = "mock:result")
    private MockEndpoint mock;
    
    @Test
    public void kmsListKeysTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listKeys", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KMSConstants.OPERATION, KMSOperations.listKeys);
            }
        });

        assertMockEndpointsSatisfied();
        
        ListKeysResult resultGet = (ListKeysResult) exchange.getIn().getBody();
        assertEquals(1, resultGet.getKeys().size());
        assertEquals("keyId", resultGet.getKeys().get(0).getKeyId());
    }
    
    @Test
    public void kmsCreateKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createKey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KMSConstants.OPERATION, KMSOperations.createKey);
            }
        });

        assertMockEndpointsSatisfied();
        
        CreateKeyResult resultGet = (CreateKeyResult) exchange.getIn().getBody();
        assertEquals("test", resultGet.getKeyMetadata().getKeyId());
        assertEquals(true, resultGet.getKeyMetadata().isEnabled());
    }
    
    @Test
    public void kmsDisableKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        template.request("direct:disableKey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KMSConstants.OPERATION, KMSOperations.disableKey);
                exchange.getIn().setHeader(KMSConstants.KEY_ID, "test");
            }
        });

        assertMockEndpointsSatisfied();
        
    }
    
    @Test
    public void kmsEnableKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        template.request("direct:enableKey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KMSConstants.OPERATION, KMSOperations.enableKey);
                exchange.getIn().setHeader(KMSConstants.KEY_ID, "test");
            }
        });

        assertMockEndpointsSatisfied();
        
    }
    
    @Test
    public void kmsScheduleKeyDeletionTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:scheduleDelete", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KMSConstants.OPERATION, KMSOperations.scheduleKeyDeletion);
                exchange.getIn().setHeader(KMSConstants.KEY_ID, "test");
            }
        });

        assertMockEndpointsSatisfied();
        
        ScheduleKeyDeletionResult resultGet = (ScheduleKeyDeletionResult) exchange.getIn().getBody();
        assertEquals("test", resultGet.getKeyId());
    }
    
    @Test
    public void kmsDescribeKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeKey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KMSConstants.OPERATION, KMSOperations.describeKey);
                exchange.getIn().setHeader(KMSConstants.KEY_ID, "test");
            }
        });

        assertMockEndpointsSatisfied();
        
        DescribeKeyResult resultGet = exchange.getIn().getBody(DescribeKeyResult.class);
        assertEquals("test", resultGet.getKeyMetadata().getKeyId());
        assertEquals("MyCamelKey", resultGet.getKeyMetadata().getDescription());
        assertFalse(resultGet.getKeyMetadata().isEnabled());
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        
        AmazonKMSClientMock clientMock = new AmazonKMSClientMock();
        
        registry.bind("amazonKmsClient", clientMock);
        
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:listKeys")
                    .to("aws-kms://test?kmsClient=#amazonKmsClient&operation=listKeys")
                    .to("mock:result");
                from("direct:createKey")
                    .to("aws-kms://test?kmsClient=#amazonKmsClient&operation=createKey")
                    .to("mock:result");
                from("direct:disableKey")
                    .to("aws-kms://test?kmsClient=#amazonKmsClient&operation=disableKey")
                    .to("mock:result");
                from("direct:enableKey")
                    .to("aws-kms://test?kmsClient=#amazonKmsClient&operation=enableKey")
                    .to("mock:result");
                from("direct:scheduleDelete")
                    .to("aws-kms://test?kmsClient=#amazonKmsClient&operation=scheduleKeyDeletion")
                    .to("mock:result");
                from("direct:describeKey")
                    .to("aws-kms://test?kmsClient=#amazonKmsClient&operation=describeKey")
                    .to("mock:result");
            }
        };
    }
}