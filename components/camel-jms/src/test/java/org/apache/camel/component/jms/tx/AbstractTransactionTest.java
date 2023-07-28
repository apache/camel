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

import org.apache.camel.Channel;
import org.apache.camel.DelegateProcessor;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.component.jms.AbstractSpringJMSTestSupport;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.errorhandler.DeadLetterChannel;
import org.apache.camel.processor.errorhandler.DefaultErrorHandler;
import org.apache.camel.spring.spi.TransactionErrorHandler;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.springframework.context.support.AbstractXmlApplicationContext;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test case derived from: http://camel.apache.org/transactional-client.html and Martin Krasser's sample:
 * http://www.nabble.com/JMS-Transactions---How-To-td15168958s22882.html#a15198803
 */
@Tags({ @Tag("not-parallel"), @Tag("spring"), @Tag("tx") })
public abstract class AbstractTransactionTest extends AbstractSpringJMSTestSupport {

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        setCamelContextService(null);
        context = null;
        template = null;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/tx/JavaDSLTransactionTest.xml");
    }

    protected void assertResult() {
        // should be 1 completed and 1 failed
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).create();

        template.sendBody("activemq:queue:AbstractTransactionTest", "blah");

        notify.matchesWaitTime();

        assertEquals(2, getConditionalExceptionProcessor().getCount(),
                "Expected only 2 calls to process() (1 failure, 1 success) but encountered "
                                                                       + getConditionalExceptionProcessor().getCount() + ".");
    }

    protected ConditionalExceptionProcessor getConditionalExceptionProcessor() {
        Route route = context.getRoutes().get(0);
        assertNotNull(route);
        return getConditionalExceptionProcessor(route);
    }

    /**
     * By default routes should be wrapped in the {@link DeadLetterChannel} so lets unwrap that and return the actual
     * processor
     */
    protected ConditionalExceptionProcessor getConditionalExceptionProcessor(Route route) {
        // the following is very specific (and brittle) and is not generally
        // useful outside these transaction tests (nor intended to be).
        DefaultRoute consumerRoute = assertIsInstanceOf(DefaultRoute.class, route);
        Processor processor = findProcessorByClass(consumerRoute.getProcessor(), ConditionalExceptionProcessor.class);
        return assertIsInstanceOf(ConditionalExceptionProcessor.class, processor);
    }

    protected Processor findProcessorByClass(Processor processor, Class<?> findClass) {
        while (true) {
            processor = unwrapDeadLetter(processor);

            if (processor instanceof Channel) {
                processor = ((Channel) processor).getNextProcessor();
            } else if (processor instanceof DelegateProcessor) {
                // TransactionInterceptor is a DelegateProcessor
                processor = ((DelegateProcessor) processor).getProcessor();
            } else if (processor instanceof Pipeline) {
                for (Processor p : ((Pipeline) processor).next()) {
                    p = findProcessorByClass(p, findClass);
                    if (p != null && p.getClass().isAssignableFrom(findClass)) {
                        processor = p;
                        return processor;
                    }
                }
            } else {
                return processor;
            }
        }
    }

    private Processor unwrapDeadLetter(Processor processor) {
        while (true) {
            if (processor instanceof Channel) {
                processor = ((Channel) processor).getNextProcessor();
            } else if (processor instanceof DeadLetterChannel) {
                processor = ((DeadLetterChannel) processor).getOutput();
            } else if (processor instanceof DefaultErrorHandler) {
                processor = ((DefaultErrorHandler) processor).getOutput();
            } else if (processor instanceof TransactionErrorHandler) {
                processor = ((TransactionErrorHandler) processor).getOutput();
            } else {
                return processor;
            }
        }
    }
}
