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
package org.apache.camel.component.vm;

import java.util.Iterator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.QueueReference;

/**
 *
 */
public class VmComponentReferenceEndpointTest extends ContextTestSupport {
    
    public void testVmComponentReference() throws Exception {
        VmComponent vm = context.getComponent("vm", VmComponent.class);

        String key = vm.getQueueKey("vm://foo");
        assertEquals(1, vm.getQueues().get(key).getCount());
        assertEquals(2, numberOfReferences(vm));

        // add a second consumer on the endpoint
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("vm:foo?blockWhenFull=true").routeId("foo2").to("mock:foo2");
            }
        });

        assertEquals(2, vm.getQueues().get(key).getCount());
        assertEquals(3, numberOfReferences(vm));

        // remove the 1st route
        context.stopRoute("foo");
        context.removeRoute("foo");

        assertEquals(1, vm.getQueues().get(key).getCount());
        assertEquals(2, numberOfReferences(vm));

        // remove the 2nd route
        context.stopRoute("foo2");
        context.removeRoute("foo2");

        // and there is no longer queues for the foo key
        assertNull(vm.getQueues().get(key));

        // there should still be a bar
        assertEquals(1, numberOfReferences(vm));
        key = vm.getQueueKey("vm://bar");
        assertEquals(1, vm.getQueues().get(key).getCount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("vm:foo").routeId("foo").to("mock:foo");

                from("vm:bar").routeId("bar").to("mock:bar");
            }
        };
    }
    
    private int numberOfReferences(VmComponent vm) {
        int num = 0;
        Iterator<QueueReference> it = vm.getQueues().values().iterator();
        while (it.hasNext()) {
            num += it.next().getCount();
        }
        return num;
    }

}
