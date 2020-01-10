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
package org.apache.camel.component.quartz.cron;

import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.quartz.BaseQuartzTest;
import org.apache.camel.component.quartz.QuartzEndpoint;
import org.junit.Test;

public class QuartzCronMappingTest extends BaseQuartzTest {
    protected MockEndpoint resultEndpoint;

    @Test
    public void test5PartsCronPattern() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("cron://myName?schedule=* * * * ?")
                        .id("cron")
                        .to("mock:result");
            }
        });
        context.start();
        assertEquals("0 * * * * ?", getQuartzEndpoint(context.getRoute("cron")).getCron());
    }

    @Test
    public void test6PartsCronPattern() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("cron://myName?schedule=0/2 * * * * ?")
                        .id("cron")
                        .to("mock:result");
            }
        });
        context.start();
        assertEquals("0/2 * * * * ?", getQuartzEndpoint(context.getRoute("cron")).getCron());
    }

    @Test
    public void testAdditionalProperties() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("cron://myName?schedule=0/2+*+*+*+*+?&synchronous=true")
                        .id("cron")
                        .to("mock:result");
            }
        });
        context.start();
        assertEquals("0/2 * * * * ?", getQuartzEndpoint(context.getRoute("cron")).getCron());
        assertTrue(getQuartzEndpoint(context.getRoute("cron")).isSynchronous());
    }

    private QuartzEndpoint getQuartzEndpoint(Route route) {
        Endpoint endpoint = route.getEndpoint();
        while (endpoint instanceof DelegateEndpoint) {
            endpoint = ((DelegateEndpoint) endpoint).getEndpoint();
        }
        return (QuartzEndpoint) endpoint;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

}
