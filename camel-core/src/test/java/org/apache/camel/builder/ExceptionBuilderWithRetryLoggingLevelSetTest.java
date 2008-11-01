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
package org.apache.camel.builder;

import java.io.IOException;
import java.net.ConnectException;
import java.security.GeneralSecurityException;
import java.security.KeyException;
import java.security.KeyManagementException;

import javax.naming.Context;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.LoggingLevel;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.jndi.JndiContext;
import org.apache.commons.logging.Log;

/**
 * Unit test to test exception configuration
 */
public class ExceptionBuilderWithRetryLoggingLevelSetTest extends ContextTestSupport {

    private static final String MESSAGE_INFO = "messageInfo";
    private static final String RESULT_QUEUE = "mock:result";
    private static final String ERROR_QUEUE = "mock:error";
   
    public void testExceptionIsLoggedWithCustomLogLevel() throws Exception {
        MockEndpoint result = getMockEndpoint(RESULT_QUEUE);
        result.expectedMessageCount(0);
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm somekind of IO exception");

        try {
            template.sendBody("direct:a", "Hello IO");
            fail("Should have thrown a IOException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof IOException);
            // expected
        }

        MockEndpoint.assertIsSatisfied(result, mock);        
        assertTrue(getCustomLog().loggedTrace && getCustomLog().loggedFatal);
    }   
    
    public void testExceptionIsLoggedWithDefaultLevel() throws Exception {
        MockEndpoint result = getMockEndpoint(RESULT_QUEUE);
        result.expectedMessageCount(0);
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm a NPE");

        try {
            template.sendBody("direct:a", "Hello NPE");
            fail("Should have thrown a NullPointerException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
            // expected
        }

        MockEndpoint.assertIsSatisfied(result, mock);
        assertTrue(!getCustomLog().loggedTrace && !getCustomLog().loggedFatal);
    }    

    @Override
    protected void setUp() throws Exception {
        CustomLog logger = getCustomLog();
        if (logger != null) {
            // reinit state
            logger.loggedFatal = false;
            logger.loggedTrace = false;
        }
        super.setUp();
    }   
    
    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("theCustomLog", new CustomLog());
        answer.bind("myExceptionThrowingProcessor", new MyExceptionThrowingProcessor());
        return answer;
    }    
    
    private CustomLog getCustomLog() {
        if (context != null) {
            return context.getRegistry().lookup("theCustomLog", CustomLog.class);
        } else {
            return null;
        }
    }   
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {             
                errorHandler(deadLetterChannel().log(getCustomLog()));
                
                onException(NullPointerException.class)
                    .maximumRedeliveries(0)
                    .setHeader(MESSAGE_INFO, constant("Damm a NPE"))
                    .to(ERROR_QUEUE);                
                
                // START SNIPPET: exceptionBuilder1
                onException(IOException.class)
                    .initialRedeliveryDelay(1000L)
                    .maximumRedeliveries(3)
                    .maximumRedeliveryDelay(10000L)
                    .backOffMultiplier(1.0)
                    .useExponentialBackOff()
                    .retryAttemptedLogLevel(LoggingLevel.TRACE)
                    .retriesExhaustedLogLevel(LoggingLevel.FATAL)
                    .setHeader(MESSAGE_INFO, constant("Damm somekind of IO exception"))
                    .to(ERROR_QUEUE);
                // END SNIPPET: exceptionBuilder1

                from("direct:a")
                    .processRef("myExceptionThrowingProcessor").to("mock:result");
            }
        };
    }
}


