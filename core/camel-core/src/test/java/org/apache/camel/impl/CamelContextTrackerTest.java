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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CamelContextTracker;
import org.junit.Assert;
import org.junit.Test;

public class CamelContextTrackerTest extends Assert {

    private final class MyContextTracker extends CamelContextTracker {

        private List<String> names = new ArrayList<>();

        @Override
        public void contextCreated(CamelContext camelContext) {
            names.add(camelContext.getName());
        }

        @Override
        public void contextDestroyed(CamelContext camelContext) {
            names.remove(camelContext.getName());
        }
    }

    @Test
    public void testContainerSet() throws Exception {
        MyContextTracker tracker = new MyContextTracker();

        CamelContext camel1 = new DefaultCamelContext();
        CamelContext camel2 = new DefaultCamelContext();
        assertEquals(0, tracker.names.size());

        try {
            tracker.open();
            assertEquals(0, tracker.names.size());

            CamelContext camel3 = new DefaultCamelContext();
            assertEquals(1, tracker.names.size());
            assertEquals(camel3.getName(), tracker.names.get(0));

            camel1.stop();
            camel2.stop();
            camel3.stop();

            assertEquals(0, tracker.names.size());

        } finally {
            tracker.close();
        }
    }
}
