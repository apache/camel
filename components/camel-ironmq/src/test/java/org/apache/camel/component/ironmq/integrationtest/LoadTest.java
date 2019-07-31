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
package org.apache.camel.component.ironmq.integrationtest;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dataset.DataSetSupport;
import org.apache.camel.component.ironmq.IronMQConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Integration test that requires ironmq account.")
public class LoadTest extends CamelTestSupport {
    private static final String IRONMQCLOUD = "http://mq-v3-aws-us-east-1.iron.io";

    // replace with your project id
    private final String projectId = "replace-this";
    // replace with your token
    private final String token = "replace-this";

    private final String ironMQEndpoint = "ironmq:testqueue?preserveHeaders=true&projectId=" + projectId + "&token=" + token
                                          + "&maxMessagesPerPoll=100&delay=3000&wait=30&ironMQCloud=" + IRONMQCLOUD;
    private final String datasetEndpoint = "dataset:foo?produceDelay=5";
    private InputDataset dataSet = new InputDataset(1000);

    @Before
    public void clearQueue() {
        // make sure the queue is empty before test
        template.sendBodyAndHeader(ironMQEndpoint, null, IronMQConstants.OPERATION, IronMQConstants.CLEARQUEUE);
    }

    @Test
    public void testDataSet() throws Exception {
        MockEndpoint endpoint = getMockEndpoint(datasetEndpoint);
        endpoint.expectedMessageCount((int)dataSet.getSize());

        assertMockEndpointsSatisfied(4, TimeUnit.MINUTES);
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("foo", dataSet);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(datasetEndpoint).to(ironMQEndpoint);
                from(ironMQEndpoint).to(datasetEndpoint);
            }
        };
    }

    public class InputDataset extends DataSetSupport {

        public InputDataset(int size) {
            super(size);
        }

        @Override
        protected Object createMessageBody(long messageIndex) {
            return "<hello>" + messageIndex;
        }

    }
}
