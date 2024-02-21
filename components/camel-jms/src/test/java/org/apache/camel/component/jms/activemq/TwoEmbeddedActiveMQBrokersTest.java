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
package org.apache.camel.component.jms.activemq;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisVMService;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.AbstractXmlApplicationContext;

@Tags({ @Tag("not-parallel"), @Tag("spring") })
public class TwoEmbeddedActiveMQBrokersTest extends CamelSpringTestSupport {

    @RegisterExtension
    public static ArtemisService firstBroker = new ArtemisVMService();

    @RegisterExtension
    public static ArtemisService secondBroker = new ArtemisVMService();

    /**
     * Used by spring xml configurations
     *
     * @return
     */
    public static String getFirstBrokerServiceAddress() {
        return firstBroker.serviceAddress();
    }

    public static String getSecondBrokerServiceAddress() {
        return secondBroker.serviceAddress();
    }

    @Test
    public void sendToTwoEmbeddedBrokers() throws Exception {
        getMockEndpoint("mock:JmsTransferExchangeFromSplitterTest1").expectedMessageCount(1);
        getMockEndpoint("mock:JmsTransferExchangeFromSplitterTest2").expectedMessageCount(1);

        template.sendBody("activemq1:queue:JmsTransferExchangeFromSplitterTest1", "foo");
        template.sendBody("activemq2:queue:JmsTransferExchangeFromSplitterTest2", "bar");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("classpath:org/apache/camel/component/jms/artemis/twoActiveMQBrokers.xml");
    }

}
