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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit6.CamelSpringTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * With a persistent job store, the trigger of a route that keeps it (deleteJob=false, also with pauseJob=true) is
 * stored paused when the application stops, and it must fire again when the application starts again.
 */
class SpringQuartzPersistentStorePauseJobRestartAppTest {

    @Test
    void pausedTriggerFiresAgainAfterTheRestart() throws Exception {
        AbstractXmlApplicationContext app = newAppContext("SpringQuartzPersistentStorePauseJobTest.xml");
        app.start();
        CamelContext camel = app.getBean("camelContext-" + getClass().getSimpleName(), CamelContext.class);
        assertNotNull(camel);
        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMinimumMessageCount(1);
        MockEndpoint kept = camel.getEndpoint("mock:kept", MockEndpoint.class);
        kept.expectedMinimumMessageCount(1);
        MockEndpoint.assertIsSatisfied(camel);

        // the routes stop and pause their triggers in the job store
        app.stop();

        AbstractXmlApplicationContext app2 = newAppContext("SpringQuartzPersistentStorePauseJobRestartTest.xml");
        app2.start();
        CamelContext camel2 = app2.getBean("camelContext-" + getClass().getSimpleName(), CamelContext.class);
        assertNotNull(camel2);
        MockEndpoint mock2 = camel2.getEndpoint("mock:result", MockEndpoint.class);
        mock2.expectedMinimumMessageCount(1);
        MockEndpoint kept2 = camel2.getEndpoint("mock:kept", MockEndpoint.class);
        kept2.expectedMinimumMessageCount(1);
        MockEndpoint.assertIsSatisfied(camel2);

        app2.stop();
        // close the second application first, so that its scheduler shuts down properly
        IOHelper.close(app2, app);
    }

    private AbstractXmlApplicationContext newAppContext(String config) {
        return CamelSpringTestSupport.newAppContext(config, getClass());
    }
}
