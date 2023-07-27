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
package org.apache.camel.component.dataset;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ListDataSetConsumerTest extends ContextTestSupport {

    protected ListDataSet dataSet;

    final String resultUri = "mock://result";
    final String dataSetName = "foo";
    final String dataSetUri = "dataset://" + dataSetName;

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("foo", dataSet);
        return answer;
    }

    @Test
    public void testDefaultListDataSet() throws Exception {
        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMinimumMessageCount((int) dataSet.getSize());

        result.assertIsSatisfied(Duration.ofSeconds(5).toMillis());
    }

    @Test
    public void testDefaultListDataSetWithSizeGreaterThanListSize() throws Exception {
        MockEndpoint result = getMockEndpoint(resultUri);
        dataSet.setSize(10);
        result.expectedMinimumMessageCount((int) dataSet.getSize());

        result.assertIsSatisfied(Duration.ofSeconds(5).toMillis());
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        List<Object> bodies = new LinkedList<>();
        bodies.add("<hello>world!</hello>");
        dataSet = new ListDataSet(bodies);

        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(dataSetUri).to("mock://result");
            }
        };
    }
}
