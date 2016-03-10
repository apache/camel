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
package org.apache.camel.component.dataset;

import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class DataSetConsumerTest extends ContextTestSupport {
    protected SimpleDataSet dataSet = new SimpleDataSet(20);

    final String dataSetName = "foo";
    final String dataSetUri = "dataset://" + dataSetName;
    final String resultUri = "mock://result";

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        context.bind(dataSetName, dataSet);
        return context;
    }

    /**
     * Ensure the expected message count for a consumer-only endpoint defaults to zero
     */
    public void testConsumerOnlyEndpoint() throws Exception {

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(dataSetUri)
                        .to(resultUri);
            }
        });
        context.start();

        assertEquals("expectedMessageCount should be -1 for a consumer-only endpoint", -1, getMockEndpoint(dataSetUri).getExpectedCount());

        getMockEndpoint(resultUri).expectedMessageCount((int)dataSet.getSize());

        assertMockEndpointsSatisfied();
    }

    /**
     * Ensure the expected message count for a consumer-producer endpoint defaults to the size of the dataset
     */
    public void testConsumerWithProducer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(dataSetUri)
                        .to(dataSetUri)
                        .to(resultUri);
            }
        });
        context.start();

        assertEquals("expectedMessageCount should be the same as the DataSet size for a consumer-producer endpoint", dataSet.getSize(), getMockEndpoint(dataSetUri).getExpectedCount());

        getMockEndpoint(resultUri).expectedMessageCount((int)dataSet.getSize());

        assertMockEndpointsSatisfied();
    }
}