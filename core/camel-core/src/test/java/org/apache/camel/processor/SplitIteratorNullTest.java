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
package org.apache.camel.processor;

import java.util.Iterator;
import java.util.function.Consumer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class SplitIteratorNullTest extends ContextTestSupport {

    private MyIterator myIterator = new MyIterator();

    @Test
    public void testSplitIteratorNull() throws Exception {
        assertFalse(myIterator.isNullReturned());
        getMockEndpoint("mock:line").expectedBodiesReceived("A", "B", "C");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
        assertTrue(myIterator.isNullReturned());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").split(constant(myIterator)).to("mock:line");
            }
        };
    }

    private class MyIterator implements Iterator<String> {

        private int count = 4;
        private boolean nullReturned;

        @Override
        public boolean hasNext() {
            // we return true one extra time, and cause next to return null
            return count > 0;
        }

        @Override
        public String next() {
            count--;
            if (count == 0) {
                nullReturned = true;
                return null;
            } else if (count == 1) {
                return "C";
            } else if (count == 2) {
                return "B";
            } else {
                return "A";
            }
        }

        public boolean isNullReturned() {
            return nullReturned;
        }

        @Override
        public void remove() {
            // noop
        }

        @Override
        public void forEachRemaining(Consumer<? super String> action) {
            // noop
        }
    }
}
