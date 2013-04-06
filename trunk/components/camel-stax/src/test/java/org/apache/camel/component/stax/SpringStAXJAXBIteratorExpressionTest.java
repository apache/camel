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
package org.apache.camel.component.stax;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.stax.model.Record;
import org.apache.camel.component.stax.model.RecordsUtil;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringStAXJAXBIteratorExpressionTest extends CamelSpringTestSupport {

    @EndpointInject(uri = "mock:records")
    private MockEndpoint recordsEndpoint;

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/stax/SpringStAXJAXBIteratorExpressionTest.xml");
    }

    @BeforeClass
    public static void initRouteExample() {
        RecordsUtil.createXMLFile();
    }

    @Test
    public void testStaxExpression() throws InterruptedException {
        recordsEndpoint.expectedMessageCount(10);
        recordsEndpoint.allMessages().body().isInstanceOf(Record.class);

        recordsEndpoint.assertIsSatisfied();

        Record five = recordsEndpoint.getReceivedExchanges().get(4).getIn().getBody(Record.class);
        assertEquals("4", five.getKey());
        assertEquals("#4", five.getValue());
    }

}
