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
package org.apache.camel.routepolicy.quartz;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.TestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.Test;
import org.quartz.Scheduler;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Tests a Quartz based cluster setup of two Camel Apps being triggered through {@link CronScheduledRoutePolicy}.
 */
public class SpringQuartzTwoAppsClusteredFailoverTest extends TestSupport {

    @Test
    public void testQuartzPersistentStoreClusteredApp() throws Exception {
        // boot up the database the two apps are going to share inside a clustered quartz setup
        AbstractXmlApplicationContext db = new ClassPathXmlApplicationContext("org/apache/camel/routepolicy/quartz/SpringQuartzClusteredAppDatabase.xml");

        // now launch the first clustered app which will acquire the quartz database lock and become the master
        AbstractXmlApplicationContext app = new ClassPathXmlApplicationContext("org/apache/camel/routepolicy/quartz/SpringQuartzClusteredAppOne.xml");

        // as well as the second one which will run in slave mode as it will not be able to acquire the same lock
        AbstractXmlApplicationContext app2 = new ClassPathXmlApplicationContext("org/apache/camel/routepolicy/quartz/SpringQuartzClusteredAppTwo.xml");

        CamelContext camel = app.getBean("camelContext", CamelContext.class);

        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("clustering PINGS!");

        // wait a bit to make sure the route has already been properly started through the given route policy
        Thread.sleep(5000);

        app.getBean("template", ProducerTemplate.class).sendBody("direct:start", "clustering");

        mock.assertIsSatisfied();

        // now let's simulate a crash of the first app (the quartz instance 'app-one')
        log.warn("The first app is going to crash NOW!");
        // we need to stop the Scheduler first as the CamelContext will gracefully shutdown and
        // delete all scheduled jobs, so there would be nothing for the second CamelContext to
        // failover from
        app.getBean(Scheduler.class).shutdown();
        IOHelper.close(app);

        log.warn("Crashed...");
        log.warn("Crashed...");
        log.warn("Crashed...");

        // wait long enough until the second app takes it over...
        Thread.sleep(20000);
        // inside the logs one can then clearly see how the route of the second app ('app-two') gets started:
        // 2013-09-29 08:15:17,038 [main           ] WARN  tzTwoAppsClusteredFailoverTest:65   - Crashed...
        // 2013-09-29 08:15:17,038 [main           ] WARN  tzTwoAppsClusteredFailoverTest:66   - Crashed...
        // 2013-09-29 08:15:17,038 [main           ] WARN  tzTwoAppsClusteredFailoverTest:67   - Crashed...
        // 2013-09-29 08:15:32,001 [_ClusterManager] INFO  LocalDataSourceJobStore       :3567 - ClusterManager: detected 1 failed or restarted instances.
        // 2013-09-29 08:15:32,001 [_ClusterManager] INFO  LocalDataSourceJobStore       :3426 - ClusterManager: Scanning for instance "app-one"'s failed in-progress jobs.
        // 2013-09-29 08:15:32,024 [eduler_Worker-1] INFO  SpringCamelContext            :2183 - Route: myRoute started and consuming from: Endpoint[direct://start]

        CamelContext camel2 = app2.getBean("camelContext2", CamelContext.class);

        MockEndpoint mock2 = camel2.getEndpoint("mock:result", MockEndpoint.class);
        mock2.expectedMessageCount(1);
        mock2.expectedBodiesReceived("clustering PONGS!");

        app2.getBean("template", ProducerTemplate.class).sendBody("direct:start", "clustering");

        mock2.assertIsSatisfied();

        // and as the last step shutdown the second app as well as the database
        IOHelper.close(app2, db);
    }

}
