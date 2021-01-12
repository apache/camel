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
package org.apache.camel.spring.handler;

import org.apache.camel.Processor;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ErrorHandlerDefinitionParserTest {
    protected ClassPathXmlApplicationContext ctx;

    @BeforeEach
    public void setUp() throws Exception {
        ctx = new ClassPathXmlApplicationContext("org/apache/camel/spring/handler/ErrorHandlerDefinitionParser.xml");
    }

    @AfterEach
    public void tearDown() throws Exception {
        IOHelper.close(ctx);
    }

    @Test
    public void testDefaultErrorHandler() {
        DefaultErrorHandlerBuilder errorHandler = ctx.getBean("defaultErrorHandler", DefaultErrorHandlerBuilder.class);
        assertNotNull(errorHandler);
        RedeliveryPolicy policy = errorHandler.getRedeliveryPolicy();
        assertNotNull(policy);
        assertEquals(2, policy.getMaximumRedeliveries(), "Wrong maximumRedeliveries");
        assertEquals(0, policy.getRedeliveryDelay(), "Wrong redeliveryDelay");
        assertEquals(false, policy.isLogStackTrace(), "Wrong logStackTrace");

        errorHandler = ctx.getBean("errorHandler", DefaultErrorHandlerBuilder.class);
        assertNotNull(errorHandler);
    }

    @Test
    public void testTransactionErrorHandler() {
        TransactionErrorHandlerBuilder errorHandler
                = ctx.getBean("transactionErrorHandler", TransactionErrorHandlerBuilder.class);
        assertNotNull(errorHandler);
        assertNotNull(errorHandler.getTransactionTemplate());
        Processor processor = errorHandler.getOnRedelivery();
        assertTrue(processor instanceof MyErrorProcessor, "It should be MyErrorProcessor");
    }

    @Test
    public void testTXErrorHandler() {
        TransactionErrorHandlerBuilder errorHandler = ctx.getBean("txEH", TransactionErrorHandlerBuilder.class);
        assertNotNull(errorHandler);
        assertNotNull(errorHandler.getTransactionTemplate());
    }

    @Test
    public void testDeadLetterErrorHandler() {
        DeadLetterChannelBuilder errorHandler = ctx.getBean("deadLetterErrorHandler", DeadLetterChannelBuilder.class);
        assertNotNull(errorHandler);
        assertEquals("log:dead", errorHandler.getDeadLetterUri(), "Get wrong deadletteruri");
        RedeliveryPolicy policy = errorHandler.getRedeliveryPolicy();
        assertNotNull(policy);
        assertEquals(2, policy.getMaximumRedeliveries(), "Wrong maximumRedeliveries");
        assertEquals(1000, policy.getRedeliveryDelay(), "Wrong redeliveryDelay");
        assertEquals(true, policy.isLogHandled(), "Wrong logStackTrace");
        assertEquals(true, policy.isAsyncDelayedRedelivery(), "Wrong asyncRedeliveryDelayed");
    }

}
