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
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cronService option selects a bean by name, or else the cron service of the classpath by its id, and fails when
 * neither matches.
 */
public class CronServiceByIdTest extends CamelTestSupport {

    @Test
    void cronServiceSelectsTheCronServiceFileOfItsId() throws Exception {
        startWithCronService("other");

        assertInstanceOf(OtherCamelCronService.class, cronComponent().getService());
    }

    @Test
    void cronServiceMatchesTheIdOfTheCronServiceOfTheClasspath() throws Exception {
        startWithCronService("dummy");

        assertEquals("dummy", cronComponent().getService().getId());
    }

    @Test
    void cronServiceThatMatchesNoBeanAndNoIdFails() {
        Exception e = assertThrows(Exception.class, () -> startWithCronService("nosuch"));
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("nosuch"), cause.getMessage());
        assertTrue(cause.getMessage().contains("the cron service on the classpath has the id dummy"), cause.getMessage());
    }

    @Test
    void beanWinsOverTheIdOfTheClasspath() throws Exception {
        context.getRegistry().bind("other", new DummyCamelCronService("bean"));

        startWithCronService("other");

        assertEquals("bean", cronComponent().getService().getId());
    }

    private CronComponent cronComponent() {
        return context.getComponent("cron", CronComponent.class);
    }

    private void startWithCronService(String cronService) throws Exception {
        cronComponent().setCronService(cronService);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("cron:tab?schedule=0/1 * * * * ?").to("mock:result");
            }
        });
        context.start();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
