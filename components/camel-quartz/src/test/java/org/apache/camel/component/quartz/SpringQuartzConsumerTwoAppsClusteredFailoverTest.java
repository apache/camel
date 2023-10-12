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
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.camel.util.IOHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;

/**
 * Tests a Quartz based cluster setup of two Camel Apps being triggered through {@link QuartzConsumer}.
 */
public class SpringQuartzConsumerTwoAppsClusteredFailoverTest {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Test
    public void testQuartzPersistentStoreClusteredApp() throws Exception {
        // boot up the database the two apps are going to share inside a clustered quartz setup
        AbstractXmlApplicationContext db = newAppContext("SpringQuartzConsumerClusteredAppDatabase.xml");
        db.start();

        // now launch the first clustered app which will acquire the quartz database lock and become the master
        AbstractXmlApplicationContext app = newAppContext("SpringQuartzConsumerClusteredAppOne.xml");
        app.start();

        // as well as the second one which will run in slave mode as it will not be able to acquire the same lock
        AbstractXmlApplicationContext app2 = newAppContext("SpringQuartzConsumerClusteredAppTwo.xml");
        app2.start();

        CamelContext camel = app.getBean("camelContext-" + getClass().getSimpleName(), CamelContext.class);

        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMinimumMessageCount(3);
        mock.expectedMessagesMatches(new ClusteringPredicate(true));

        mock.assertIsSatisfied();

        // now let's simulate a crash of the first app (the quartz instance 'app-one')
        log.warn("The first app is going to crash NOW!");
        IOHelper.close(app);

        log.warn("Crashed...");
        log.warn("Crashed...");
        log.warn("Crashed...");

        // wait long enough until the second app takes it over...
        Awaitility.await().untilAsserted(() -> {
            CamelContext camel2 = app2.getBean("camelContext2-" + getClass().getSimpleName(), CamelContext.class);

            MockEndpoint mock2 = camel2.getEndpoint("mock:result", MockEndpoint.class);
            mock2.expectedMinimumMessageCount(3);
            mock2.expectedMessagesMatches(new ClusteringPredicate(false));

            mock2.assertIsSatisfied();
        });
        // inside the logs one can then clearly see how the route of the second app ('app-two') starts consuming:
        // 2013-09-30 11:22:20,349 [main           ] WARN  erTwoAppsClusteredFailoverTest - Crashed...
        // 2013-09-30 11:22:20,349 [main           ] WARN  erTwoAppsClusteredFailoverTest - Crashed...
        // 2013-09-30 11:22:20,349 [main           ] WARN  erTwoAppsClusteredFailoverTest - Crashed...
        // 2013-09-30 11:22:35,340 [_ClusterManager] INFO  LocalDataSourceJobStore        - ClusterManager: detected 1 failed or restarted instances.
        // 2013-09-30 11:22:35,340 [_ClusterManager] INFO  LocalDataSourceJobStore        - ClusterManager: Scanning for instance "app-one"'s failed in-progress jobs.
        // 2013-09-30 11:22:35,369 [eduler_Worker-1] INFO  triggered                      - Exchange[ExchangePattern: InOnly, BodyType: String, Body: clustering PONGS!]

        // and as the last step shutdown the second app as well as the database
        IOHelper.close(app2, db);
    }

    private AbstractXmlApplicationContext newAppContext(String config) {
        return CamelSpringTestSupport.newAppContext(config, getClass());
    }

    private static class ClusteringPredicate implements Predicate {

        private final String expectedPayload;

        ClusteringPredicate(boolean pings) {
            expectedPayload = pings ? "clustering PINGS!" : "clustering PONGS!";
        }

        @Override
        public boolean matches(Exchange exchange) {
            return exchange.getIn().getBody().equals(expectedPayload);
        }

    }

}
