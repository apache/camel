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
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class DataSetProducerTest extends ContextTestSupport {
    protected SimpleDataSet dataSet = new SimpleDataSet(20);

    final String dataSetName = "foo";
    final String dataSetUri = "dataset://" + dataSetName;
    final String dataSetUriWithDisableDataSetIndexSetToFalse = dataSetUri + "?disableDataSetIndex=false";
    final String dataSetUriWithDisableDataSetIndexSetToTrue = dataSetUri + "?disableDataSetIndex=true";
    final String sourceUri = "direct://source";
    final String resultUri = "mock://result";

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        context.bind(dataSetName, dataSet);
        return context;
    }

    @Test
    public void testSendingMessagesExplicitlyToDataSetEndpoint() throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            template.sendBodyAndHeader(dataSetUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendingMessagesExplicitlyToDataSetEndpointWithoutDataSetIndex() throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            template.sendBody(dataSetUri, dataSet.getDefaultBody());
        }

        assertMockEndpointsSatisfied();
    }

    /**
     * Verfiy that the CamelDataSetIndex header is optional when the disableDataSetIndex parameter is unset
     */
    @Test
    public void testNotSettingDataSetIndexHeaderWhenDisableDataSetIndexUriParameterIsUnset() throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            if (0 == (size % 2)) {
                template.sendBodyAndHeader(dataSetUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i);
            } else {
                template.sendBody(dataSetUri, dataSet.getDefaultBody());
            }
        }

        assertMockEndpointsSatisfied();
    }

    /**
     * Verfiy tha the CamelDataSetIndex header is optional when the disableDataSetIndex parameter is true
     */
    @Test
    public void testNotSettingDataSetIndexHeaderWhenDisableDataSetIndexUriParameterSetToTrue() throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            if (0 == (size % 2)) {
                template.sendBodyAndHeader(dataSetUriWithDisableDataSetIndexSetToTrue, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i);
            } else {
                template.sendBody(dataSetUriWithDisableDataSetIndexSetToTrue, dataSet.getDefaultBody());
            }
        }
        for (long i = 0; i < size; i++) {
        }

        assertMockEndpointsSatisfied();
    }

    /**
     * Verify tha the CamelDataSetIndex header is required when the disableDataSetIndex parameter is false
     */
    @Test
    public void testNotSettingDataSetIndexHeaderWhenDisableDataSetIndexUriParameterSetToFalse() throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            template.sendBody(dataSetUriWithDisableDataSetIndexSetToFalse, dataSet.getDefaultBody());
        }

        try {
            getMockEndpoint(dataSetUriWithDisableDataSetIndexSetToFalse).assertIsSatisfied();
        } catch (AssertionError assertionError) {
            // Check as much of the string as possible - but the ExchangeID at the end will be unique
            String expectedErrorString = dataSetUriWithDisableDataSetIndexSetToFalse
                    + " Failed due to caught exception: "
                    + NoSuchHeaderException.class.getName()
                    + ": No '" + Exchange.DATASET_INDEX
                    + "' header available of type: java.lang.Long. Exchange";
            String actualErrorString = assertionError.getMessage();
            if (actualErrorString.startsWith(expectedErrorString)) {
                // This is what we expect
                return;
            } else {
                throw assertionError;
            }
        }

        fail("AssertionError should have been generated");
    }

    @Test
    public void testSendingMessagesExplicitlyToDataSetEndpointWithoutDataSetIndexAndDisableDataSetIndexUriParameterSetToTrue() throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            template.sendBody(dataSetUriWithDisableDataSetIndexSetToTrue, dataSet.getDefaultBody());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDisableDataSetIndexUriParameterUnset() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(sourceUri)
                        .to(dataSetUri)
                        .to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();
        result.expectsAscending(header(Exchange.DATASET_INDEX).convertTo(Number.class));

        for (long i = 0; i < size; i++) {
            template.sendBody(sourceUri, dataSet.getDefaultBody());
        }

        assertMockEndpointsSatisfied();

        result.assertMessagesAscending(header(Exchange.DATASET_INDEX).convertTo(Number.class));
    }

    @Test
    public void testDisableDataSetIndexUriParameterSetToTrue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(sourceUri)
                        .to(dataSetUriWithDisableDataSetIndexSetToTrue)
                        .to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);
        result.allMessages().header(Exchange.DATASET_INDEX).isNull();

        for (long i = 0; i < size; i++) {
            template.sendBody(sourceUri, dataSet.getDefaultBody());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDisableDataSetIndexUriParameterSetToFalse() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(sourceUri)
                        .to(dataSetUriWithDisableDataSetIndexSetToFalse)
                        .to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);
        result.expectsAscending(header(Exchange.DATASET_INDEX).convertTo(Number.class));
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();

        for (long i = 0; i < size; i++) {
            template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i);
        }

        assertMockEndpointsSatisfied();
    }


    @Test
    public void testInvalidDataSetIndexValueWithDisableDataSetIndexUriParameterUnset() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(sourceUri)
                        .to(dataSetUri)
                        .to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();

        for (long i = 0; i < size; i++) {
            if (i == (size / 2)) {
                template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i + 10);
            } else {
                template.sendBody(sourceUri, dataSet.getDefaultBody());
            }
        }

        try {
            assertMockEndpointsSatisfied();
        } catch (AssertionError assertionError) {
            // Check as much of the string as possible - but the ExchangeID at the end will be unique
            String expectedErrorString = dataSetUri + " Failed due to caught exception: "
                    + AssertionError.class.getName()
                    + ": Header: " + Exchange.DATASET_INDEX + " does not match. Expected: "
                    + size / 2 + " but was: " + (size / 2 + 10) + " on Exchange";
            String actualErrorString = assertionError.getMessage();
            if (actualErrorString.startsWith(expectedErrorString)) {
                // This is what we expect
                return;
            } else {
                throw assertionError;
            }
        }

        fail("AssertionError should have been generated");
    }

    @Test
    public void testInvalidDataSetIndexValueWithDisableDataSetIndexUriParameterSetToTrue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(sourceUri)
                        .to(dataSetUriWithDisableDataSetIndexSetToTrue)
                        .to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);

        for (long i = 0; i < size; i++) {
            if (i == (size / 2)) {
                template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i + 10);
            } else {
                template.sendBody(sourceUri, dataSet.getDefaultBody());
            }
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInvalidDataSetIndexValueWithDisableDataSetIndexUriParameterSetToFalse() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(sourceUri)
                        .to(dataSetUriWithDisableDataSetIndexSetToFalse)
                        .to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();

        for (long i = 0; i < size; i++) {
            if (i == (size / 2)) {
                template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i + 10);
            } else {
                template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i);
            }
        }

        try {
            assertMockEndpointsSatisfied();
        } catch (AssertionError assertionError) {
            // Check as much of the string as possible - but the ExchangeID at the end will be unique
            String expectedErrorString = dataSetUriWithDisableDataSetIndexSetToFalse + " Failed due to caught exception: "
                    + AssertionError.class.getName() + ": Header: " + Exchange.DATASET_INDEX
                    + " does not match. Expected: " + size / 2 + " but was: " + (size / 2 + 10) + " on Exchange";
            String actualErrorString = assertionError.getMessage();
            if (actualErrorString.startsWith(expectedErrorString)) {
                // This is what we expect
                return;
            } else {
                throw assertionError;
            }
        }

        fail("AssertionError should have been generated");
    }
}