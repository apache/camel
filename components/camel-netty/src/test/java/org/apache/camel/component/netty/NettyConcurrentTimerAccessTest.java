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
package org.apache.camel.component.netty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.Test;

public class NettyConcurrentTimerAccessTest extends BaseNettyTest {

    int secondPort = AvailablePortFinder.getNextAvailable(25000);

    @Test
    public void stoppingOneComponentShouldNotAffectTheOther() throws Exception {
        context.getComponent("netty1", NettyComponent.class).stop();
        template.sendBody("netty2:tcp://localhost:" + secondPort + "/pleaseCreateNewEndpoint", "msg");
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("netty1", new NettyComponent());
        registry.bind("netty2", new NettyComponent());
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("netty1:tcp://localhost:{{port}}").to("mock:test");
                from("netty2:tcp://localhost:" + secondPort).to("mock:test");
            }
        };
    }

}
