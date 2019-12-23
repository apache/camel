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
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 */
public class JMSTransactionRollbackTest extends CamelSpringTestSupport {

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
            "/org/apache/camel/component/jms/tx/JMSTransactionRollbackTest.xml");
    }

    @Test
    public void testTransactionRollback() throws Exception {
        getMockEndpoint("mock:before").expectedMessageCount(6);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("activemq:queue:okay", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public static class MyProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            throw new IllegalArgumentException("Forced Exception");
        }
    }

}