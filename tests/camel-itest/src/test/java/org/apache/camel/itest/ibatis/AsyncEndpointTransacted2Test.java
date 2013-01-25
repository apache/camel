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
package org.apache.camel.itest.ibatis;

import java.util.Collection;
import java.util.Iterator;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class AsyncEndpointTransacted2Test extends CamelSpringTestSupport {
    @EndpointInject(uri = "direct:start")
    ProducerTemplate producer;
    
    private DummyTable table;

    @EndpointInject(uri = "mock:end")
    private MockEndpoint end;

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/ibatis/AsyncEndpointTransacted2Test.xml");
    }

    @Before
    public void createTable() {
        table = context.getRegistry().lookupByNameAndType("table", DummyTable.class);
        table.create();
    }

    @After
    public void dropTable() {
        table.drop();
    }

    @Test
    public void testAsyncEndpointOK() throws InterruptedException {
        end.expectedBodiesReceived("Bye Camel");

        producer.sendBody(3);

        Collection<Integer> values = table.values();
        assertEquals(2, values.size());

        Iterator<Integer> it = values.iterator();
        assertEquals(Integer.valueOf(3), it.next());
        assertEquals(Integer.valueOf(4), it.next());

        end.assertIsSatisfied();
    }

    @Test
    public void testAsyncEndpointRollback() {
        end.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                throw new IllegalArgumentException("Damn");
            }
        });

        try {
            producer.sendBody(3);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause());
            assertEquals(0, table.values().size());
        }
    }

}