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

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * End user on forum issue
 */
public class TransactionInterceptSendToEndpointTest extends CamelSpringTestSupport {

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
            "/org/apache/camel/component/jms/tx/TransactionInterceptSendToEndpointTest.xml");
    }


    @Test
    public void testIntercepted() throws Exception {
        getMockEndpoint("mock:end").expectedMessageCount(1);
        getMockEndpoint("mock:detour").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(0);

        template.sendBody("activemq:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

}