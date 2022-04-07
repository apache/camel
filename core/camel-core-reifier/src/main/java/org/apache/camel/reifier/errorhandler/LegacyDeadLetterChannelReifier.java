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
package org.apache.camel.reifier.errorhandler;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.errorhandler.DeadLetterChannelProperties;
import org.apache.camel.processor.FatalFallbackErrorHandler;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.errorhandler.DeadLetterChannel;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.util.ObjectHelper;

/**
 * Legacy error handler for XML DSL in camel-spring-xml/camel-blueprint
 */
@Deprecated
public class LegacyDeadLetterChannelReifier extends LegacyDefaultErrorHandlerReifier<DeadLetterChannelProperties> {

    public LegacyDeadLetterChannelReifier(Route route, ErrorHandlerFactory definition) {
        super(route, definition);
    }

    @Override
    public Processor createErrorHandler(Processor processor) throws Exception {
        ObjectHelper.notNull(definition.getDeadLetterUri(), "deadLetterUri", this);

        // optimize to use shared default instance if using out of the box settings
        RedeliveryPolicy redeliveryPolicy
                = definition.hasRedeliveryPolicy() ? definition.getRedeliveryPolicy() : definition.getDefaultRedeliveryPolicy();
        CamelLogger logger = definition.hasLogger() ? definition.getLogger() : null;

        Processor deadLetterProcessor = createDeadLetterChannelProcessor(definition.getDeadLetterUri());

        DeadLetterChannel answer = new DeadLetterChannel(
                camelContext, processor, logger,
                getProcessor(definition.getOnRedelivery(), definition.getOnRedeliveryRef()),
                redeliveryPolicy, deadLetterProcessor,
                definition.getDeadLetterUri(), definition.isDeadLetterHandleNewException(), definition.isUseOriginalMessage(),
                definition.isUseOriginalBody(),
                definition.getRetryWhilePolicy(camelContext),
                getExecutorService(definition.getExecutorService(), definition.getExecutorServiceRef()),
                getProcessor(definition.getOnPrepareFailure(), definition.getOnPrepareFailureRef()),
                getProcessor(definition.getOnExceptionOccurred(), definition.getOnExceptionOccurredRef()));
        // configure error handler before we can use it
        configure(answer);
        return answer;
    }

    private Processor createDeadLetterChannelProcessor(String uri) {
        // wrap in our special safe fallback error handler if sending to
        // dead letter channel fails
        Processor child = new SendProcessor(camelContext.getEndpoint(uri), ExchangePattern.InOnly);
        // force MEP to be InOnly so when sending to DLQ we would not expect
        // a reply if the MEP was InOut
        return new FatalFallbackErrorHandler(child, true);
    }

}
