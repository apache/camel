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
import org.apache.camel.test.junit4.TestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringQuartzPersistentStoreRestartAppChangeOptionsTest extends TestSupport {

    private static AbstractXmlApplicationContext db;
    private AbstractXmlApplicationContext app;
    private AbstractXmlApplicationContext app2;
    private AbstractXmlApplicationContext app3;


    @BeforeClass
    public static void prepareDB() {
        // boot up the database the two invocations are going to share inside a clustered quartz setup
        db = new ClassPathXmlApplicationContext("org/apache/camel/component/quartz/SpringQuartzConsumerClusteredAppDatabase.xml");
        db.start();
    }

    @AfterClass
    public static void shutdownDB() {
        db.close();
    }

    @After
    public void closeApps() {
        // we're done so let's properly close the application contexts, but close
        // the second app before the first one so that the quartz scheduler running
        // inside it can be properly shutdown
        IOHelper.close(app3, app2, app);
    }


    @Test
    public void testRestartAppChangeCronExpression() throws Exception {

        // Test creates application context twice with different cron expressions in configuration xml.
        // Both times it retrieves back the cron expression, accessing it via trigger (so, using value stored in DB).
        // After that it asserts that two cron expressions are not equal.

        // load spring app
        app = new ClassPathXmlApplicationContext("org/apache/camel/component/quartz/SpringQuartzPersistentStoreRestartAppChangeCronExpressionTest1.xml");
        app.start();
        CamelContext camel = app.getBean("camelContext", CamelContext.class);
        assertNotNull(camel);
        String cronExpression = ((CronTrigger) getTrigger(camel, "quartzRoute")).getCronExpression();
        app.stop();

        log.info("Restarting ...");
        log.info("Restarting ...");
        log.info("Restarting ...");

        // load spring app
        app2 = new ClassPathXmlApplicationContext("org/apache/camel/component/quartz/SpringQuartzPersistentStoreRestartAppChangeCronExpressionTest2.xml");
        app2.start();
        CamelContext camel2 = app2.getBean("camelContext", CamelContext.class);
        assertNotNull(camel2);
        String cronExpression2 = ((CronTrigger) getTrigger(camel2, "quartzRoute")).getCronExpression();
        app2.stop();

        assertNotEquals(cronExpression, cronExpression2);

        // load spring app
        app3 = new ClassPathXmlApplicationContext("org/apache/camel/component/quartz/SpringQuartzPersistentStoreRestartAppChangeCronExpressionTest3.xml");
        app3.start();
        CamelContext camel3 = app3.getBean("camelContext3", CamelContext.class);
        assertNotNull(camel3);
        String cronExpression3 = ((CronTrigger) getTrigger(camel3, "quartzRoute")).getCronExpression();
        app3.stop();

        assertEquals(cronExpression2, cronExpression3);
    }

    @Test
    public void testRestartAppChangeTriggerOptions() throws Exception {

        // Test creates application context twice with different simple trigger options in configuration xml.
        // Both times it retrieves back the option, accessing it via trigger (so, using value stored in DB).
        // After that it asserts that two options are not equal.

        // load spring app
        AbstractXmlApplicationContext app = new ClassPathXmlApplicationContext("org/apache/camel/component/quartz/SpringQuartzPersistentStoreRestartAppChangeOptionsTest1.xml");
        app.start();
        CamelContext camel = app.getBean("camelContext", CamelContext.class);
        assertNotNull(camel);
        SimpleTrigger trigger = (SimpleTrigger) getTrigger(camel, "quartzRoute");
        long repeatInterval = trigger.getRepeatInterval();
        app.stop();

        log.info("Restarting ...");
        log.info("Restarting ...");
        log.info("Restarting ...");

        // load spring app
        AbstractXmlApplicationContext app2 = new ClassPathXmlApplicationContext("org/apache/camel/component/quartz/SpringQuartzPersistentStoreRestartAppChangeOptionsTest2.xml");
        app2.start();
        CamelContext camel2 = app2.getBean("camelContext", CamelContext.class);
        assertNotNull(camel2);
        SimpleTrigger trigger2 = (SimpleTrigger) getTrigger(camel2, "quartzRoute");
        long repeatInterval2 = trigger2.getRepeatInterval();
        app2.stop();

        // we're done so let's properly close the application contexts, but close
        // the second app before the first one so that the quartz scheduler running
        // inside it can be properly shutdown
        IOHelper.close(app2, app);

        assertNotEquals(repeatInterval, repeatInterval2);
    }

    @Test
    public void testRestartAppChangeTriggerType() throws Exception {

        // Test creates application context twice with different simple trigger options in configuration xml.
        // Both times it retrieves back the option, accessing it via trigger (so, using value stored in DB).
        // After that it asserts that two options are not equal.

        // load spring app
        app = new ClassPathXmlApplicationContext("org/apache/camel/component/quartz/SpringQuartzPersistentStoreRestartAppChangeCronExpressionTest1.xml");
        app.start();
        CamelContext camel = app.getBean("camelContext", CamelContext.class);
        assertNotNull(camel);
        assertTrue(getTrigger(camel, "quartzRoute") instanceof CronTrigger);
        app.stop();

        log.info("Restarting ...");
        log.info("Restarting ...");
        log.info("Restarting ...");

        // load spring app
        AbstractXmlApplicationContext app2 = new ClassPathXmlApplicationContext("org/apache/camel/component/quartz/SpringQuartzPersistentStoreRestartAppChangeOptionsTest2.xml");
        app2.start();
        CamelContext camel2 = app2.getBean("camelContext", CamelContext.class);
        assertNotNull(camel2);
        assertTrue(getTrigger(camel2, "quartzRoute") instanceof SimpleTrigger);
        app2.stop();

        // we're done so let's properly close the application contexts, but close
        // the second app before the first one so that the quartz scheduler running
        // inside it can be properly shutdown
        IOHelper.close(app2, app);
    }

    private Trigger getTrigger(CamelContext camel, String routeId) throws SchedulerException {
        QuartzEndpoint endpoint = (QuartzEndpoint) (camel.getRoute(routeId).getEndpoint());
        QuartzComponent component = endpoint.getComponent();
        Scheduler scheduler = component.getScheduler();
        return scheduler.getTrigger(endpoint.getTriggerKey());
    }

}
