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
package org.apache.camel.component.jms.integration.spring.tx;

import org.apache.camel.Channel;
import org.apache.camel.DelegateProcessor;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.component.jms.integration.spring.AbstractSpringJMSITSupport;
import org.apache.camel.component.jms.integration.spring.tx.support.ConditionalExceptionProcessor;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.errorhandler.DeadLetterChannel;
import org.apache.camel.processor.errorhandler.DefaultErrorHandler;
import org.apache.camel.spring.spi.TransactionErrorHandler;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.springframework.context.support.AbstractXmlApplicationContext;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test case derived from: http://camel.apache.org/transactional-client.html and Martin Krasser's sample:
 * http://www.nabble.com/JMS-Transactions---How-To-td15168958s22882.html#a15198803
 */
@Tags({ @Tag("not-parallel"), @Tag("spring"), @Tag("tx") })
public abstract class AbstractTransactionIT extends AbstractSpringJMSITSupport {

    @Override
    public void doPostTearDown() {
        setCamelContextService(null);
        context = null;
        template = null;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/jms/integration/spring/tx/JavaDSLTransactionTest.xml");
    }

    protected void assertResult() {
        // should be 1 completed and 1 failed
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).create();

        template.sendBody("activemq:queue:AbstractTransactionTest", "blah");

        notify.matchesWaitTime();

        Assertions.assertEquals(2, getConditionalExceptionProcessor().getCount(),
                "Expected only 2 calls to process() (1 failure, 1 success) but encountered "
                                                                                  + getConditionalExceptionProcessor()
                                                                                          .getCount()
                                                                                  + ".");
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

            if (processor instanceof Channel channel) {
                processor = channel.getNextProcessor();
            } else if (processor instanceof DelegateProcessor delegateProcessor) {
                // TransactionInterceptor is a DelegateProcessor
                processor = delegateProcessor.getProcessor();
            } else if (processor instanceof Pipeline pipeline) {
                for (Processor p : pipeline.next()) {
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
            if (processor instanceof Channel channel) {
                processor = channel.getNextProcessor();
            } else if (processor instanceof DeadLetterChannel deadLetterChannel) {
                processor = deadLetterChannel.getOutput();
            } else if (processor instanceof DefaultErrorHandler defaultErrorHandler) {
                processor = defaultErrorHandler.getOutput();
            } else if (processor instanceof TransactionErrorHandler transactionErrorHandler) {
                processor = transactionErrorHandler.getOutput();
            } else {
                return processor;
            }
        }
    }
}
