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
package org.apache.camel.component.quartz;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

public class QuartzStartDelayedWithReferenceTest extends QuartzStartDelayedTest {
    
    // just bind the reference value here
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("interval", new Long(2));
        registry.bind("count", new Integer(1));
        return registry;
    }
    
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                QuartzComponent quartz = context.getComponent("quartz", QuartzComponent.class);
                quartz.setStartDelayedSeconds(2);

                from("quartz://myGroup/myTimerName?trigger.repeatInterval=#interval&trigger.repeatCount=#count").routeId("myRoute").to("mock:result");
            }
        };
    }

}
