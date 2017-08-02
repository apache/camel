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
package org.apache.camel.spring.example;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class PojoDualCamelContextConsumerTest extends TestSupport {
    private CamelContext camel1;
    private CamelContext camel2;
    private ApplicationContext ac;

    public void testCamel1() throws Exception {
        String body = "<hello>world!</hello>";

        MockEndpoint result = camel1.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived(body);

        ProducerTemplate template = camel1.createProducerTemplate();
        template.start();
        template.sendBody("direct:start", body);
        template.stop();

        result.assertIsSatisfied();
    }

    public void testCamel2() throws Exception {
        String body = "<bye>world!</bye>";

        MockEndpoint result = camel2.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived(body);

        ProducerTemplate template = camel2.createProducerTemplate();
        template.start();
        template.sendBody("direct:start", body);
        template.stop();

        result.assertIsSatisfied();
    }

    public void testCamel1RecipientList() throws Exception {
        String body = "<hello>world!</hello>";

        // seda:foo has no consumer in camel-1 so we should not expect any messages to be routed to result/foo
        MockEndpoint result = camel1.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedMessageCount(0);
        result.setResultMinimumWaitTime(50);

        ProducerTemplate template = camel1.createProducerTemplate();
        template.start();
        template.sendBody("seda:foo", body);
        template.stop();

        result.assertIsSatisfied();
    }

    public void testCamel2RecipientList() throws Exception {
        String body = "<bye>world!</bye>";

        MockEndpoint result = camel2.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived(body);

        MockEndpoint foo = camel2.getEndpoint("mock:foo", MockEndpoint.class);
        foo.expectedBodiesReceived(body);

        ProducerTemplate template = camel2.createProducerTemplate();
        template.start();
        template.sendBody("direct:foo", body);
        template.stop();

        result.assertIsSatisfied();
        foo.assertIsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        ac = new ClassPathXmlApplicationContext("org/apache/camel/spring/example/pojoDualCamelContextConsumer.xml");
        camel1 = ac.getBean("camel-1", CamelContext.class);
        camel2 = ac.getBean("camel-2", CamelContext.class);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        camel1.stop();
        camel2.stop();
    }

}