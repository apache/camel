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
package org.apache.camel.spi;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;

public class ContainerTest extends TestCase {

    private final class MyContainer implements Container {

        private List<String> names = new ArrayList<String>();

        @Override
        public void manage(CamelContext camelContext) {
            names.add(camelContext.getName());
        }
    }

    @Override
    protected void tearDown() throws Exception {
        Container.Instance.set(null);
        super.tearDown();
    }

    public void testContainerSet() throws Exception {
        MyContainer myContainer = new MyContainer();

        CamelContext camel1 = new DefaultCamelContext();
        CamelContext camel2 = new DefaultCamelContext();

        // Must call start to make contexts 'managed'
        camel1.start();
        camel2.start();

        assertEquals(0, myContainer.names.size());
        Container.Instance.set(myContainer);
        // after we set, then we should manage the 2 pending contexts
        assertEquals(2, myContainer.names.size());

        CamelContext camel3 = new DefaultCamelContext();
        camel3.start();

        assertEquals(3, myContainer.names.size());
        assertEquals(camel1.getName(), myContainer.names.get(0));
        assertEquals(camel2.getName(), myContainer.names.get(1));
        assertEquals(camel3.getName(), myContainer.names.get(2));

        camel1.stop();
        camel2.stop();
        camel3.stop();
    }

    public void testNoContainerSet() throws Exception {
        MyContainer myContainer = new MyContainer();

        CamelContext camel1 = new DefaultCamelContext();
        CamelContext camel2 = new DefaultCamelContext();

        camel1.start();
        camel2.start();

        assertEquals(0, myContainer.names.size());

        camel1.stop();
        camel2.stop();
    }
}
