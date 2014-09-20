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
package org.apache.camel.spring.handler;

import junit.framework.TestCase;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.LoggingErrorHandlerBuilder;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.apache.camel.util.IOHelper;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ErrorHandlerDefinitionParserTest extends TestCase {
    protected ClassPathXmlApplicationContext ctx;
    
    public void setUp() throws Exception {
        ctx =  new ClassPathXmlApplicationContext("org/apache/camel/spring/handler/ErrorHandlerDefinitionParser.xml");
    }

    public void tearDown() throws Exception {
        IOHelper.close(ctx);
    }
    
    public void testLoggingErrorHandler() {
        LoggingErrorHandlerBuilder errorHandler = ctx.getBean("loggingErrorHandler", LoggingErrorHandlerBuilder.class);
        assertNotNull(errorHandler);
        assertEquals("The log level should be INFO", LoggingLevel.INFO, errorHandler.getLevel());
        assertEquals("The log name should be foo", "foo", errorHandler.getLogName());
    }
    
    public void testDefaultErrorHandler() {
        DefaultErrorHandlerBuilder errorHandler = ctx.getBean("defaultErrorHandler", DefaultErrorHandlerBuilder.class);
        assertNotNull(errorHandler);
        RedeliveryPolicy policy = errorHandler.getRedeliveryPolicy();
        assertNotNull(policy);
        assertEquals("Wrong maximumRedeliveries", 2, policy.getMaximumRedeliveries());
        assertEquals("Wrong redeliveryDelay", 0, policy.getRedeliveryDelay());
        assertEquals("Wrong logStackTrace", false, policy.isLogStackTrace());
        
        errorHandler = ctx.getBean("errorHandler", DefaultErrorHandlerBuilder.class);
        assertNotNull(errorHandler);
    }
    
    public void testTransactionErrorHandler() {
        TransactionErrorHandlerBuilder errorHandler = ctx.getBean("transactionErrorHandler", TransactionErrorHandlerBuilder.class);
        assertNotNull(errorHandler);
        assertNotNull(errorHandler.getTransactionTemplate());
        Processor processor = errorHandler.getOnRedelivery();
        assertTrue("It should be MyErrorProcessor", processor instanceof MyErrorProcessor);
    }
    
    public void testTXErrorHandler() {
        TransactionErrorHandlerBuilder errorHandler = ctx.getBean("txEH", TransactionErrorHandlerBuilder.class);
        assertNotNull(errorHandler);
        assertNotNull(errorHandler.getTransactionTemplate());
    }

    public void testDeadLetterErrorHandler() {
        DeadLetterChannelBuilder errorHandler = ctx.getBean("deadLetterErrorHandler", DeadLetterChannelBuilder.class);
        assertNotNull(errorHandler);
        assertEquals("Get wrong deadletteruri", "log:dead", errorHandler.getDeadLetterUri());
        RedeliveryPolicy policy = errorHandler.getRedeliveryPolicy();
        assertNotNull(policy);
        assertEquals("Wrong maximumRedeliveries", 2, policy.getMaximumRedeliveries());
        assertEquals("Wrong redeliveryDelay", 1000, policy.getRedeliveryDelay());
        assertEquals("Wrong logStackTrace", true, policy.isLogHandled());
        assertEquals("Wrong asyncRedeliveryDelayed", true, policy.isAsyncDelayedRedelivery());
    }

}

