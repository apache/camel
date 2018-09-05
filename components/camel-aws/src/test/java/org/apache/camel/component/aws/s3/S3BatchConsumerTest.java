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
package org.apache.camel.component.aws.s3;

import com.amazonaws.services.s3.model.S3Object;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class S3BatchConsumerTest extends CamelTestSupport {
    
    @EndpointInject(uri = "mock:result")
    private MockEndpoint mock;
        
    @Test
    public void receiveBatch() throws Exception {
        mock.expectedMessageCount(5);
        assertMockEndpointsSatisfied();
        
        mock.message(0).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(0);
        mock.message(1).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(1);
        mock.message(2).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(2);
        mock.message(3).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(3);
        mock.message(4).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(4);
        mock.message(0).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(1).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(2).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(3).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(3).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(4).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(true);
        mock.expectedPropertyReceived(Exchange.BATCH_SIZE, 5);
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        
        AmazonS3ClientMock clientMock = new AmazonS3ClientMock();
        // add 6 messages, one more we will poll
        for (int counter = 0; counter < 6; counter++) {
            S3Object s3Object = new S3Object();
            s3Object.setBucketName("mycamelbucket");
            s3Object.setKey("counter-" + counter);
            
            clientMock.objects.add(s3Object);
        }
        
        registry.bind("amazonS3Client", clientMock);
        
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("aws-s3://mycamelbucket?amazonS3Client=#amazonS3Client&region=us-west-1&delay=5000&maxMessagesPerPoll=5")
                    .to("mock:result");
            }
        };
    }
}