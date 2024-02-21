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
package org.apache.camel.component.jms;

import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * JMS with XPath
 */
@Tags({ @Tag("not-parallel"), @Tag("spring") })
public class SpringJmsXPathHeaderTest extends AbstractSpringJMSTestSupport {

    @Test
    public void testTrue() throws Exception {
        getMockEndpoint("mock:true").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBodyAndHeader("activemq:queue:SpringJmsXPathHeaderTest.in", "<hello>World</hello>", "foo", "true");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFalse() throws Exception {
        getMockEndpoint("mock:true").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBodyAndHeader("activemq:queue:SpringJmsXPathHeaderTest.in", "<hello>World</hello>", "foo", "false");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testNoHeader() throws Exception {
        getMockEndpoint("mock:true").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBody("activemq:queue:SpringJmsXPathHeaderTest.in", "<hello>World</hello>");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/SpringJmsXPathHeaderTest.xml");
    }

}
