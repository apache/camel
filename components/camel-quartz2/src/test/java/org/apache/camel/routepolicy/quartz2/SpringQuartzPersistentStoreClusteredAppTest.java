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
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.TestSupport;

import org.junit.Test;

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class SpringQuartzPersistentStoreClusteredAppTest extends TestSupport {

    @Test
    public void testQuartzPersistentStoreClusteredApp() throws Exception {
        // boot up the first clustered app which also launches an embedded database
        AbstractXmlApplicationContext app = new ClassPathXmlApplicationContext("org/apache/camel/routepolicy/quartz2/SpringQuartzClusteredAppOneTest.xml");
        app.start();

        // and now the second one
        AbstractXmlApplicationContext app2 = new ClassPathXmlApplicationContext("org/apache/camel/routepolicy/quartz2/SpringQuartzClusteredAppTwoTest.xml");
        app2.start();

        CamelContext camel = app.getBean("camelContext", CamelContext.class);
        assertNotNull(camel);

        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("clustering pings!");

        // wait a bit to make sure the route has been properly started through
        // the given route policy
        Thread.sleep(5000);

        app.getBean("template", ProducerTemplate.class).sendBody("direct:start", "clustering");

        mock.assertIsSatisfied();

        CamelContext camel2 = app2.getBean("camelContext", CamelContext.class);
        assertNotNull(camel2);

        MockEndpoint mock2 = camel2.getEndpoint("mock:result", MockEndpoint.class);
        mock2.expectedMessageCount(0);

        // expect no consumer being started as the seconds app is expected to
        // run in standby modus
        try {
            app2.getBean("template", ProducerTemplate.class).sendBody("direct:start", "clustering");
            fail("Should have thrown exception");
        } catch (CamelExecutionException cee) {
            assertIsInstanceOf(DirectConsumerNotAvailableException.class, cee.getCause());
        }

        mock2.assertIsSatisfied();

        // we're done so let's properly close the application contexts, but stop
        // the second app before the first one so that the quartz scheduler running
        // inside it can properly be shutdown
        app2.close();
        app.close();
    }

}
