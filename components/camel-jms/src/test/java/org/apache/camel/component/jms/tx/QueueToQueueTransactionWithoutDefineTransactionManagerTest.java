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

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;

public class QueueToQueueTransactionWithoutDefineTransactionManagerTest extends AbstractTransactionTest {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/tx/ActiveMQWithoutTransactionManager.xml");
    }
    
    @Test
    public void testNoTransactionRollbackUsingXmlQueueToQueue() throws Exception {

        // configure routes and add to camel context
        context.addRoutes(new SpringRouteBuilder() {

            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());
                from("activemq:queue:foo?transacted=false").process(new ConditionalExceptionProcessor())
                        .to("activemq:queue:bar?transacted=false");


            }
        });

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        template.sendBody("activemq:queue:foo", "blah");

        notify.matchesMockWaitTime();

        assertTrue("Expected only 1 calls to process() (1 failure) but encountered "
                   + getConditionalExceptionProcessor().getCount() + ".", getConditionalExceptionProcessor().getCount() == 1);
    }
    
}
