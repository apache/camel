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
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.processor.Logger;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.commons.logging.LogFactory;

/**
 * A builder of a <a
 * href="http://camel.apache.org/dead-letter-channel.html">Dead Letter
 * Channel</a>
 *
 * @version $Revision$
 */
public class DeadLetterChannelBuilder extends DefaultErrorHandlerBuilder {

    public DeadLetterChannelBuilder() {
        // no-arg constructor used by Spring DSL
    }

    public DeadLetterChannelBuilder(Endpoint deadLetter) {
        setDeadLetter(deadLetter);
    }

    public DeadLetterChannelBuilder(String uri) {
        setDeadLetterUri(uri);
    }

    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        DeadLetterChannel answer = new DeadLetterChannel(processor, getLogger(), getOnRedelivery(), getRedeliveryPolicy(),
                getHandledPolicy(), getExceptionPolicyStrategy(), getFailureProcessor(), getDeadLetterUri(),
                isUseOriginalMessage());
        // configure error handler before we can use it
        configure(answer);
        return answer;
    }

    public boolean supportTransacted() {
        return false;
    }

    // Properties
    // -------------------------------------------------------------------------

    public Processor getFailureProcessor() {
        if (failureProcessor == null) {
            if (deadLetter != null) {
                failureProcessor = new SendProcessor(deadLetter);
            } else {
                // use a recipient list since we only have an uri for the endpoint
                failureProcessor = new RecipientList(new Expression() {
                    public Object evaluate(Exchange exchange) {
                        return deadLetterUri;
                    }

                    public <T> T evaluate(Exchange exchange, Class<T> type) {
                        return exchange.getContext().getTypeConverter().convertTo(type, deadLetterUri);
                    }
                });
            }
        }
        return failureProcessor;
    }

    protected Predicate createHandledPolicy() {
        // should be handled by default for dead letter channel
        return PredicateBuilder.toPredicate(ExpressionBuilder.constantExpression(true));
    }

    @Override
    protected RedeliveryPolicy createRedeliveryPolicy() {
        return new RedeliveryPolicy();
    }

    protected Logger createLogger() {
        return new Logger(LogFactory.getLog(DeadLetterChannel.class), LoggingLevel.ERROR);
    }

    @Override
    public String toString() {
        return "DeadLetterChannelBuilder(" + deadLetterUri + ")";
    }
}
