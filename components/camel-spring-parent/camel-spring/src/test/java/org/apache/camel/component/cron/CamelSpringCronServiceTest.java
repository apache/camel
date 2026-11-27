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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spring cron service refuses a schedule with a year, which a Spring cron expression cannot have, and is found by
 * its id.
 */
public class CamelSpringCronServiceTest {

    private CamelContext context;

    @BeforeEach
    void createContext() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void stopContext() {
        context.stop();
    }

    @Test
    void sevenPartScheduleFailsTheEndpoint() {
        Exception e = assertThrows(Exception.class, () -> context.getEndpoint("cron:tab?schedule=0+0/2+*+*+*+MON-FRI+2030"));
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("spring cron service") && cause.getMessage().contains("year"),
                cause.getMessage());
    }

    @Test
    void sixPartScheduleCreatesTheEndpoint() {
        CronEndpoint endpoint = context.getEndpoint("cron:tab?schedule=0+0/2+*+*+*+MON-FRI", CronEndpoint.class);

        assertInstanceOf(SpringCronEndpoint.class, endpoint.getEndpoint());
    }

    @Test
    void cronServiceSpringSelectsTheSpringCronService() {
        context.getComponent("cron", CronComponent.class).setCronService("spring");

        context.getEndpoint("cron:tab?schedule=0+0/2+*+*+*+MON-FRI", CronEndpoint.class);

        assertInstanceOf(CamelSpringCronService.class, context.getComponent("cron", CronComponent.class).getService());
    }
}
