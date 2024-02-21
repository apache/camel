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
package org.apache.camel.component.quartz;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class QuartzComponentCamelContextSchedulerIsolationTest {

    @AfterAll
    public static void afterTests() {
    }

    @Test
    public void testSchedulerIsolationUnmanaged() throws Exception {
        DefaultCamelContext.setDisableJmx(true);
        testSchedulerIsolation();
    }

    @Test
    public void testSchedulerIsolationManaged() throws Exception {
        DefaultCamelContext.setDisableJmx(false);
        testSchedulerIsolation();
    }

    private void testSchedulerIsolation() throws Exception {
        CamelContext context = createCamelContext();
        context.start();

        CamelContext anotherContext = createCamelContext();
        assertNotEquals(anotherContext.getName(), context.getName());
        assertNotEquals(anotherContext, context);

        assertNotSame(getDefaultScheduler(context), getDefaultScheduler(anotherContext));
    }

    /**
     * Create a new camel context instance.
     */
    private DefaultCamelContext createCamelContext() {
        return new DefaultCamelContext();
    }

    /**
     * Get the quartz component for the provided camel context.
     */
    private QuartzComponent getQuartzComponent(CamelContext context) {
        return context.getComponent("quartz", QuartzComponent.class);
    }

    /**
     * Get the default scheduler for the provided camel context.
     */
    private Scheduler getDefaultScheduler(CamelContext context) {
        return getQuartzComponent(context).getScheduler();
    }

}
