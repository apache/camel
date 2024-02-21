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
package org.apache.camel.component.jms.tx;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.jms.issues.CamelBrokerClientTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JMSNestedTransactionRollbackTest extends CamelBrokerClientTestSupport {

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/org/apache/camel/component/jms/tx/JMSNestedTransactionRollbackTest.xml");
    }

    @Test
    void testNestedTransactionRolledackSuccessfully() throws Exception {
        context.start();

        // error handler should catch 1 exception and rollback producer transaction
        MockEndpoint error = getMockEndpoint("mock:got-message");
        error.expectedMessageCount(1);
        error.setAssertPeriod(100);

        // if transaction rolled back successfully, then no messages should go there
        MockEndpoint mock = getMockEndpoint("mock:not-okay");
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(100);

        template.sendBody("jms:queue:okay", "test");

        error.assertIsSatisfied();
        mock.assertIsSatisfied();
    }

    public static class ErrorThrowProcessor implements Processor {

        @Override
        public void process(Exchange exchange) {
            throw new IllegalArgumentException("Forced exception");
        }
    }

}
