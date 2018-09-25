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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class FileDataSetConsumerWithSplitTest extends ContextTestSupport {
    protected FileDataSet dataSet;

    final String testDataFileName = "src/test/data/file-dataset-test.txt";
    final int testDataFileRecordCount = 10;

    final String resultUri = "mock://result";
    final String dataSetName = "foo";
    final String dataSetUri = "dataset://" + dataSetName;

    @Test
    public void testDefaultListDataSet() throws Exception {
        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMinimumMessageCount((int) dataSet.getSize());

        result.assertIsSatisfied();
    }

    @Test
    public void testDefaultListDataSetWithSizeGreaterThanListSize() throws Exception {
        MockEndpoint result = getMockEndpoint(resultUri);
        dataSet.setSize(20);
        result.expectedMinimumMessageCount((int) dataSet.getSize());

        result.assertIsSatisfied();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        dataSet = new FileDataSet(testDataFileName, "\n");
        assertEquals("Unexpected DataSet size", testDataFileRecordCount, dataSet.getSize());
        super.setUp();
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        context.bind(dataSetName, dataSet);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(dataSetUri)
                        .to("mock://result");
            }
        };
    }
}
