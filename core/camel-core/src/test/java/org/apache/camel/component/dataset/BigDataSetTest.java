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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Manual test")
public class BigDataSetTest extends ContextTestSupport {
    protected SimpleDataSet dataSet = new SimpleDataSet(20000);

    @Test
    public void testDataSet() throws Exception {
        // data set will itself set its assertions so we should just
        // assert that all mocks is ok
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("foo", dataSet);
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // start this first to make sure the "direct:foo" consumer is
                // ready
                from("direct:foo").to("dataset:foo");

                from("dataset:foo").to("direct:foo");
            }
        };
    }
}
