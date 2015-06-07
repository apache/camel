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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ThreadPoolProfile;

/**
 * @version 
 */
public class CamelCustomDefaultThreadPoolProfileTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camel = super.createCamelContext();

        ThreadPoolProfile profile = new ThreadPoolProfile("custom");
        profile.setPoolSize(5);
        profile.setMaxPoolSize(15);
        profile.setKeepAliveTime(25L);
        profile.setMaxQueueSize(250);
        profile.setAllowCoreThreadTimeOut(true);
        profile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);

        camel.getExecutorServiceManager().setDefaultThreadPoolProfile(profile);
        return camel;
    }

    public void testCamelCustomDefaultThreadPoolProfile() throws Exception {
        DefaultExecutorServiceManager manager = (DefaultExecutorServiceManager)context.getExecutorServiceManager();
        ThreadPoolProfile profile = manager.getDefaultThreadPoolProfile();
        assertEquals(5, profile.getPoolSize().intValue());
        assertEquals(15, profile.getMaxPoolSize().intValue());
        assertEquals(25, profile.getKeepAliveTime().longValue());
        assertEquals(250, profile.getMaxQueueSize().intValue());
        assertEquals(true, profile.getAllowCoreThreadTimeOut().booleanValue());
        assertEquals(ThreadPoolRejectedPolicy.Abort, profile.getRejectedPolicy());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .threads(25, 45)
                    .to("mock:result");
            }
        };
    }
}
