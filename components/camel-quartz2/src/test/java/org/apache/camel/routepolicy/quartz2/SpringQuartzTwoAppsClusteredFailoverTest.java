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
package org.apache.camel.routepolicy.quartz2;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class SpringQuartzTwoAppsClusteredFailoverTest extends TestSupport {

    @Test
    public void testQuartzPersistentStoreClusteredApp() throws Exception {
        // boot up the database the two apps are going to share inside a clustered quartz setup
        AbstractXmlApplicationContext db = new ClassPathXmlApplicationContext("org/apache/camel/routepolicy/quartz2/SpringQuartzEmbeddedDatabase.xml");
        db.start();

        // now launch the first clustered app
        AbstractXmlApplicationContext app = new ClassPathXmlApplicationContext("org/apache/camel/routepolicy/quartz2/SpringQuartzClusteredAppOneTest.xml");
        app.start();

        // as well as the second one
        AbstractXmlApplicationContext app2 = new ClassPathXmlApplicationContext("org/apache/camel/routepolicy/quartz2/SpringQuartzClusteredAppTwoTest.xml");
        app2.start();

        CamelContext camel = app.getBean("camelContext", CamelContext.class);

        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("clustering PINGS!");

        // wait a bit to make sure the route has already been properly started through the given route policy
        Thread.sleep(5000);

        app.getBean("template", ProducerTemplate.class).sendBody("direct:start", "clustering");

        mock.assertIsSatisfied();

        // now let's simulate a crash of the first app
        log.warn("The first app is going to crash NOW!");
        app.close();

        log.warn("Crashed...");
        log.warn("Crashed...");
        log.warn("Crashed...");

        // wait long enough until the second app takes it over...
        Thread.sleep(20000);
        // inside the logs one can then clearly see how the route of the second CamelContext gets started:
        // 2013-09-24 22:51:34,215 [main           ] WARN  ersistentStoreClusteredAppTest - Crashed...
        // 2013-09-24 22:51:34,215 [main           ] WARN  ersistentStoreClusteredAppTest - Crashed...
        // 2013-09-24 22:51:34,215 [main           ] WARN  ersistentStoreClusteredAppTest - Crashed...
        // 2013-09-24 22:51:49,188 [_ClusterManager] INFO  LocalDataSourceJobStore        - ClusterManager: detected 1 failed or restarted instances.
        // 2013-09-24 22:51:49,188 [_ClusterManager] INFO  LocalDataSourceJobStore        - ClusterManager: Scanning for instance "app-one"'s failed in-progress jobs.
        // 2013-09-24 22:51:49,211 [eduler_Worker-1] INFO  SpringCamelContext             - Route: myRoute started and consuming from: Endpoint[direct://start]

        CamelContext camel2 = app2.getBean("camelContext2", CamelContext.class);

        MockEndpoint mock2 = camel2.getEndpoint("mock:result", MockEndpoint.class);
        mock2.expectedMessageCount(1);
        mock2.expectedBodiesReceived("clustering PONGS!");

        app2.getBean("template", ProducerTemplate.class).sendBody("direct:start", "clustering");

        mock2.assertIsSatisfied();

        // stop the second app as we're already done
        app2.close();

        // and as the last step shutdown the database...
        db.close();
    }

}
