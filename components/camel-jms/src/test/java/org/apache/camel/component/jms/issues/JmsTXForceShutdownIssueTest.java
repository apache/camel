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
package org.apache.camel.component.jms.issues;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 */
public class JmsTXForceShutdownIssueTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/issues/JmsTXForceShutdownIssueTest.xml");
    }

    @Override
    protected int getShutdownTimeout() {
        // force a bit faster forced shutdown
        return 5;
    }

    @Test
    @Ignore("This is a manual test, start Apache ActiveMQ broker manually first, using bin/activemq console")
    // and make sure to setup tcp transport connector on the remote AMQ broker in the conf/activemq.xml file
    // <transportConnectors>
    //   <transportConnector name="openwire" uri="tcp://0.0.0.0:61616"/>
    // </transportConnectors>
    // the ActiveMQ web console can be used to browse the queue: http://0.0.0.0:8161/admin/
    public void testTXForceShutdown() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:inflight");
        mock.expectedMessageCount(1);

        template.sendBody("activemq:queue:inbox", "Hello World");

        assertMockEndpointsSatisfied();

        // will complete the test and force a shutdown ...
    }
}
