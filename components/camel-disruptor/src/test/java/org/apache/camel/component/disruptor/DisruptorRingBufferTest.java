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
package org.apache.camel.component.disruptor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version
 */
public class DisruptorRingBufferTest extends CamelTestSupport {
    @Test
    public void testQueue() throws Exception {
        final MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Goodday World", "Bar");

        // Following 3 calls should all reference same Disruptor ring buffer.
        template.sendBody("disruptor:foo", "Hello World");
        template.sendBody("disruptor:foo?size=1024", "Bye World");
        template.sendBody("disruptor:foo?concurrentConsumers=5", "Goodday World");

        template.sendBody("disruptor:bar", "Bar");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("disruptor:foo?concurrentConsumers=2").to("mock:result");

                from("disruptor:bar").to("mock:result");
            }
        };
    }
}
