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
package org.apache.camel.component.cron;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cron.api.CamelCronService;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CronLoaderTest extends CamelTestSupport {

    @Test
    public void testDummyCronServiceLoading() throws Exception {
        configureRoutes();
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        context.start();
        mock.assertIsSatisfied();
    }

    @Test
    public void testPreferRegistryOverServiceLoading() throws Exception {
        context.getRegistry().bind("dummy2", new DummyCamelCronService("dummy2"));
        configureRoutes();
        context.start();
        assertEquals("dummy2", getCamelCronService().getId());
    }

    @Test
    public void testUseNamesWhenLoading() throws Exception {
        context.getRegistry().bind("dummy2", new DummyCamelCronService("dummy2"));
        context.getRegistry().bind("dummy3", new DummyCamelCronService("dummy3"));
        configureRoutes();
        context.getComponent("cron", CronComponent.class).setCronService("dummy3");
        context.start();
        assertEquals("dummy3", getCamelCronService().getId());
    }

    private CamelCronService getCamelCronService() {
        return context.getComponent("cron", CronComponent.class).getService();
    }

    private void configureRoutes() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("cron:tab?schedule=0/1 * * * * ?")
                        .setBody().constant("x")
                        .to("mock:result");
            }
        });
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
