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
package org.apache.camel.component.jms.integration.spring.tx.error;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisEmbeddedServiceBuilder;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.spring.junit6.CamelSpringTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

@Tags({ @Tag("not-parallel"), @Tag("spring"), @Tag("tx") })
public class JMSTransactionRollbackIT extends CamelSpringTestSupport {

    // Use a dedicated broker with maxDeliveryAttempts=6 (1 initial + 5 retries) to match the
    // test's expectedMessageCount(6). The shared singleton broker defaults to maxDeliveryAttempts=10,
    // causing extra redeliveries that make the mock fail with "Expected 6 but was 8/10" on fast
    // machines, or "Expected 6 but was 5" on slow CI when the latch releases early.
    @RegisterExtension
    public static ArtemisService service = new ArtemisEmbeddedServiceBuilder()
            .withCustomConfiguration(configuration -> {
                AddressSettings addressSettings = new AddressSettings()
                        .setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL)
                        .setAutoDeleteQueues(false)
                        .setDeadLetterAddress(SimpleString.of("DLQ"))
                        .setExpiryAddress(SimpleString.of("ExpiryQueue"))
                        .setMaxDeliveryAttempts(6);
                configuration.addAddressSetting("#", addressSettings);
            })
            .build();

    /**
     * Used by the Spring XML context to obtain the broker address.
     *
     * @return the service address
     */
    public static String getServiceAddress() {
        return service.serviceAddress();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/org/apache/camel/component/jms/integration/spring/tx/error/JMSTransactionRollbackIT.xml");
    }

    @Test
    public void testTransactionRollback() throws Exception {
        MockEndpoint before = getMockEndpoint("mock:before");
        before.expectedMessageCount(6);
        // Guard against late-arriving extra broker redeliveries on fast machines
        before.setAssertPeriod(2000);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("activemq:queue:okay.JMSTransactionRollbackTest", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }
}
