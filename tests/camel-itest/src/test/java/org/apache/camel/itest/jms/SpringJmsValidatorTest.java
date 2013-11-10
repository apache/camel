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
package org.apache.camel.itest.jms;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class SpringJmsValidatorTest extends CamelSpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/jms/SpringJmsValidatorTest.xml");
    }

    @Test
    public void testJmsValidator() throws Exception {
        getMockEndpoint("mock:valid").expectedMessageCount(1);
        getMockEndpoint("mock:invalid").expectedMessageCount(0);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        String body = "<?xml version=\"1.0\"?>\n<p>Hello world!</p>";
        template.sendBody("jms:queue:inbox", body);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJmsValidatorInvalid() throws Exception {
        getMockEndpoint("mock:valid").expectedMessageCount(0);
        getMockEndpoint("mock:invalid").expectedMessageCount(1);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        String body = "<?xml version=\"1.0\"?>\n<foo>Kaboom</foo>";
        template.sendBody("jms:queue:inbox", body);

        assertMockEndpointsSatisfied();
    }

}