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

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.processor.FatalFallbackErrorHandler;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.LoggerFactory;

/**
 * A builder of a <a
 * href="http://camel.apache.org/dead-letter-channel.html">Dead Letter
 * Channel</a>
 *
 * @version 
 */
public class DeadLetterChannelBuilder extends DefaultErrorHandlerBuilder {

    public DeadLetterChannelBuilder() {
        // no-arg constructor used by Spring DSL
    }

    public DeadLetterChannelBuilder(Endpoint deadLetter) {
        setDeadLetter(deadLetter);
        // DLC do not log exhausted by default
        getRedeliveryPolicy().setLogExhausted(false);
    }

    public DeadLetterChannelBuilder(String uri) {
        setDeadLetterUri(uri);
    }
    
    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        validateDeadLetterUri(routeContext);

        DeadLetterChannel answer = new DeadLetterChannel(routeContext.getCamelContext(), processor, getLogger(), getOnRedelivery(), 
                getRedeliveryPolicy(), getExceptionPolicyStrategy(), getFailureProcessor(), getDeadLetterUri(), isDeadLetterHandleNewException(),
                isUseOriginalMessage(), getRetryWhilePolicy(routeContext.getCamelContext()), getExecutorService(routeContext.getCamelContext()),
                getOnPrepareFailure(), getOnExceptionOccurred());
        // configure error handler before we can use it
        configure(routeContext, answer);
        return answer;
    }

    public boolean supportTransacted() {
        return false;
    }

    @Override
    public ErrorHandlerBuilder cloneBuilder() {
        DeadLetterChannelBuilder answer = new DeadLetterChannelBuilder();
        super.cloneBuilder(answer);
        return answer;
    }

    // Properties
    // -------------------------------------------------------------------------

    public Processor getFailureProcessor() {
        if (failureProcessor == null) {
            // wrap in our special safe fallback error handler if sending to dead letter channel fails
            Processor child = new SendProcessor(deadLetter, ExchangePattern.InOnly);
            // force MEP to be InOnly so when sending to DLQ we would not expect a reply if the MEP was InOut
            failureProcessor = new FatalFallbackErrorHandler(child, true);
        }
        return failureProcessor;
    }

    protected void validateDeadLetterUri(RouteContext routeContext) {
        if (deadLetter == null) {
            ObjectHelper.notEmpty(deadLetterUri, "deadLetterUri", this);
            deadLetter = routeContext.getCamelContext().getEndpoint(deadLetterUri);
            if (deadLetter == null) {
                throw new NoSuchEndpointException(deadLetterUri);
            }
        }
    }

    protected CamelLogger createLogger() {
        return new CamelLogger(LoggerFactory.getLogger(DeadLetterChannel.class), LoggingLevel.ERROR);
    }

    @Override
    public String toString() {
        return "DeadLetterChannelBuilder(" + deadLetterUri + ")";
    }
}
