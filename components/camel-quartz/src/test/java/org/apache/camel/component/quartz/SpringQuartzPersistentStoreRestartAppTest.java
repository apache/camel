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
package org.apache.camel.component.quartz;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.TestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class SpringQuartzPersistentStoreRestartAppTest extends TestSupport {

    @Test
    public void testQuartzPersistentStoreRestart() throws Exception {
        // load spring app
        AbstractXmlApplicationContext app = new ClassPathXmlApplicationContext("org/apache/camel/component/quartz/SpringQuartzPersistentStoreTest.xml");

        app.start();

        CamelContext camel = app.getBean("camelContext", CamelContext.class);
        assertNotNull(camel);

        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMinimumMessageCount(2);

        mock.assertIsSatisfied();

        app.stop();
        
        log.info("Restarting ...");
        log.info("Restarting ...");
        log.info("Restarting ...");

        // NOTE:
        // To test a restart where the app has crashed, then you can in QuartzEndpoint
        // in the doShutdown method, then remove the following code line
        //  deleteTrigger(getTrigger());
        // then when we restart then there is old stale data which QuartzComponent
        // is supposed to handle and start again

        // load spring app
        AbstractXmlApplicationContext app2 = new ClassPathXmlApplicationContext("org/apache/camel/component/quartz/SpringQuartzPersistentStoreRestartTest.xml");

        app2.start();

        CamelContext camel2 = app2.getBean("camelContext", CamelContext.class);
        assertNotNull(camel2);

        MockEndpoint mock2 = camel2.getEndpoint("mock:result", MockEndpoint.class);
        mock2.expectedMinimumMessageCount(2);

        mock2.assertIsSatisfied();

        app2.stop();

        // we're done so let's properly close the application contexts, but close
        // the second app before the first one so that the quartz scheduler running
        // inside it can be properly shutdown
        IOHelper.close(app2, app);
    }

}
