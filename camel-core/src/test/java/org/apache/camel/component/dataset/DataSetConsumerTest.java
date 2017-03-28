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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class DataSetConsumerTest extends ContextTestSupport {
    protected SimpleDataSet dataSet = new SimpleDataSet(5);

    final String dataSetName = "foo";
    final String dataSetUri = "dataset://" + dataSetName;
    final String dataSetUriWithDataSetIndexSetToOff = dataSetUri + "?dataSetIndex=off";
    final String dataSetUriWithDataSetIndexSetToLenient = dataSetUri + "?dataSetIndex=lenient";
    final String dataSetUriWithDataSetIndexSetToStrict = dataSetUri + "?dataSetIndex=strict";
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
    @Test
    public void testConsumerOnlyEndpoint() throws Exception {

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(dataSetUri)
                        .to(resultUri);
            }
        });
        context.start();

        assertEquals("expectedMessageCount should be unset(i.e. -1) for a consumer-only endpoint", -1, getMockEndpoint(dataSetUri).getExpectedCount());

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) dataSet.getSize());
        result.assertMessagesAscending(header(Exchange.DATASET_INDEX));

        assertMockEndpointsSatisfied();
    }

    /**
     * Ensure the expected message count for a consumer-producer endpoint defaults to the size of the dataset
     */
    @Test
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

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) dataSet.getSize());
        result.expectsAscending(header(Exchange.DATASET_INDEX).convertTo(Number.class));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWithDataSetIndexUriParameterUnset() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(dataSetUri)
                        .to(resultUri);
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) dataSet.getSize());
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();
        result.expectsAscending(header(Exchange.DATASET_INDEX).convertTo(Number.class));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWithDataSetIndexUriParameterSetToOff() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(dataSetUriWithDataSetIndexSetToOff)
                        .to(resultUri);
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) dataSet.getSize());
        result.allMessages().header(Exchange.DATASET_INDEX).isNull();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWithDataSetIndexUriParameterSetToLenient() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(dataSetUriWithDataSetIndexSetToLenient)
                        .to(resultUri);
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) dataSet.getSize());
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();
        result.expectsAscending(header(Exchange.DATASET_INDEX).convertTo(Number.class));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWithDataSetIndexUriParameterSetToStrict() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(dataSetUriWithDataSetIndexSetToStrict)
                        .to(resultUri);
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) dataSet.getSize());
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();
        result.expectsAscending(header(Exchange.DATASET_INDEX).convertTo(Number.class));

        Thread.sleep(100);
        assertMockEndpointsSatisfied();
    }
}