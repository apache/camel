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
package org.apache.camel.component.jmx;

import java.io.File;

import org.apache.camel.component.jmx.beans.ISimpleMXBean;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests that trigger notification events on our simple bean without
 * requiring any special setup.
 */
public class JMXConsumerTest extends SimpleBeanFixture {

    ISimpleMXBean simpleBean;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        simpleBean = getSimpleMXBean();
    }

    @Test
    public void attributeChange() throws Exception {
        getMockFixture().getMockEndpoint().setExpectedMessageCount(1);
        simpleBean.setStringValue("foo");
        waitAndAssertMessageReceived("src/test/resources/consumer-test/attributeChange-0.xml");

        getMockFixture().getMockEndpoint().setExpectedMessageCount(1);
        simpleBean.setStringValue("bar");
        waitAndAssertMessageReceived("src/test/resources/consumer-test/attributeChange-1.xml");

        // set the string to null
        getMockFixture().getMockEndpoint().setExpectedMessageCount(1);
        simpleBean.setStringValue(null);
        waitAndAssertMessageReceived("src/test/resources/consumer-test/attributeChange-2.xml");
    }

    @Test
    public void notification() throws Exception {
        simpleBean.touch();
        waitAndAssertMessageReceived("src/test/resources/consumer-test/touched.xml");
    }

    @Test
    public void userData() throws Exception {
        simpleBean.userData("myUserData");
        waitAndAssertMessageReceived("src/test/resources/consumer-test/userdata.xml");
    }

    @Test
    public void jmxConnection() throws Exception {
        simpleBean.triggerConnectionNotification();
        waitAndAssertMessageReceived("src/test/resources/consumer-test/jmxConnectionNotification.xml");
    }

    @Test
    public void mbeanServerNotification() throws Exception {
        simpleBean.triggerMBeanServerNotification();
        waitAndAssertMessageReceived("src/test/resources/consumer-test/mbeanServerNotification.xml");
    }

    @Ignore
    public void relationNotification() throws Exception {
        simpleBean.triggerRelationNotification();
        waitAndAssertMessageReceived("src/test/resources/consumer-test/relationNotification.xml");
    }

    @Test
    public void timerNotification() throws Exception {
        simpleBean.triggerTimerNotification();
        waitAndAssertMessageReceived("src/test/resources/consumer-test/timerNotification.xml");
    }

    private void waitAndAssertMessageReceived(String aExpectedFilePath) throws InterruptedException, Exception {
        getMockFixture().waitForMessages();
        getMockFixture().assertMessageReceived(new File(aExpectedFilePath));
    }

}
