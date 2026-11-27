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

import org.apache.camel.component.cron.CronComponent;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * cronService=quartz selects the cron service of camel-quartz by its id, without a bean of that name.
 */
public class QuartzCronServiceByIdTest extends CamelTestSupport {

    @Test
    public void cronServiceQuartzSelectsTheQuartzCronService() {
        CronComponent cron = context.getComponent("cron", CronComponent.class);
        cron.setCronService("quartz");

        context.getEndpoint("cron:tab?schedule=0+0/2+*+*+*+?");

        assertInstanceOf(CamelQuartzCronService.class, cron.getService());
    }
}
