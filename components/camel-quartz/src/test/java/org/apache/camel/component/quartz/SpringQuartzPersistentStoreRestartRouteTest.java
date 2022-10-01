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

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.context.support.AbstractXmlApplicationContext;

@DisabledOnOs(OS.AIX)
public class SpringQuartzPersistentStoreRestartRouteTest extends CamelSpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return newAppContext("SpringQuartzPersistentStoreTest.xml");
    }

    @Test
    public void testQuartzPersistentStore() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        MockEndpoint.assertIsSatisfied(context);

        // restart route
        context().getRouteController().stopRoute("myRoute");
        mock.reset();
        mock.expectedMessageCount(0);

        // wait a bit
        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> MockEndpoint.assertIsSatisfied(context));

        // start route, and we got messages again
        mock.reset();
        mock.expectedMinimumMessageCount(2);

        context().getRouteController().startRoute("myRoute");

        MockEndpoint.assertIsSatisfied(context);
    }

}
