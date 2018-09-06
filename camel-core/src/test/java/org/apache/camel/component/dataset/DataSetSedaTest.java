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
import org.junit.Test;

/**
 * Unit test to demonstrate high concurrency with seda. Offspring by CAMEL-605.
 */
public class DataSetSedaTest extends ContextTestSupport {
    private SimpleDataSet dataSet = new SimpleDataSet(200);
    private String uri = "dataset:foo?initialDelay=0&produceDelay=1";

    @Test
    public void testDataSetWithSeda() throws Exception {
        MockEndpoint endpoint = getMockEndpoint(uri);
        endpoint.expectedMessageCount((int) dataSet.getSize());

        context.startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        context.bind("foo", dataSet);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(uri).to("seda:test").noAutoStartup();

                from("seda:test").to(uri);
            }
        };
    }
}
