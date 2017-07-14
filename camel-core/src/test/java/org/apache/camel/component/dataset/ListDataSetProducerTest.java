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

import java.util.LinkedList;
import java.util.List;
import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ListDataSetProducerTest extends ContextTestSupport {
    protected ListDataSet dataSet = new ListDataSet();

    final String sourceUri = "direct://source";
    final String dataSetName = "foo";
    final String dataSetUri = "dataset://" + dataSetName;

    public void testDefaultListDataSet() throws Exception {
        template.sendBodyAndHeader(dataSetUri, "<hello>world!</hello>", Exchange.DATASET_INDEX, 0);

        assertMockEndpointsSatisfied();
    }

    public void testDefaultListDataSetWithSizeGreaterThanListSize() throws Exception {
        int messageCount = 10;
        getMockEndpoint(dataSetUri).expectedMessageCount(messageCount);
        dataSet.setSize(messageCount);
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            template.sendBodyAndHeader(sourceUri, "<hello>world!</hello>", Exchange.DATASET_INDEX, i);
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    public void setUp() throws Exception {
        List<Object> bodies = new LinkedList<>();
        bodies.add("<hello>world!</hello>");
        dataSet = new ListDataSet(bodies);

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
                from(sourceUri)
                        .to(dataSetUri);
            }
        };
    }
}
