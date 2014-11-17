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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.CamelContextRegistry;

public class CamelContextRegistryTest extends TestCase {

    private final class MyListener extends CamelContextRegistry.Listener {

        private List<String> names = new ArrayList<String>();

        @Override
        public void contextAdded(CamelContext camelContext) {
            names.add(camelContext.getName());
        }

        @Override
        public void contextRemoved(CamelContext camelContext) {
            names.remove(camelContext.getName());
        }
    }

    public void testContainerSet() throws Exception {

        // must clear for testing purpose
        CamelContextRegistry.INSTANCE.clear();

        MyListener listener = new MyListener();

        CamelContext camel1 = new DefaultCamelContext();
        CamelContext camel2 = new DefaultCamelContext();

        assertEquals(0, listener.names.size());

        try {
            CamelContextRegistry.INSTANCE.addListener(listener, true);
            // after we set, then we should manage the 2 pending contexts
            assertEquals(2, listener.names.size());

            CamelContext camel3 = new DefaultCamelContext();
            assertEquals(3, listener.names.size());
            assertEquals(camel1.getName(), listener.names.get(0));
            assertEquals(camel2.getName(), listener.names.get(1));
            assertEquals(camel3.getName(), listener.names.get(2));

            camel1.stop();
            camel2.stop();
            camel3.stop();

            assertEquals(0, listener.names.size());
            
        } finally {
            CamelContextRegistry.INSTANCE.removeListener(listener, true);
        }
    }
}
