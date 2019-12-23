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

import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.processor.errorhandler.DeadLetterChannel;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.StringHelper;

public class DeadLetterChannelReifier extends DefaultErrorHandlerReifier<DeadLetterChannelBuilder> {

    public DeadLetterChannelReifier(ErrorHandlerFactory definition) {
        super(definition);
    }

    @Override
    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        validateDeadLetterUri(routeContext);

        DeadLetterChannel answer = new DeadLetterChannel(routeContext.getCamelContext(), processor, definition.getLogger(), definition.getOnRedelivery(),
                                                         definition.getRedeliveryPolicy(), definition.getExceptionPolicyStrategy(), definition.getFailureProcessor(),
                                                         definition.getDeadLetterUri(), definition.isDeadLetterHandleNewException(), definition.isUseOriginalMessage(),
                                                         definition.isUseOriginalBody(), definition.getRetryWhilePolicy(routeContext.getCamelContext()),
                                                         getExecutorService(routeContext.getCamelContext()), definition.getOnPrepareFailure(), definition.getOnExceptionOccurred());
        // configure error handler before we can use it
        configure(routeContext, answer);
        return answer;
    }

    protected void validateDeadLetterUri(RouteContext routeContext) {
        Endpoint deadLetter = definition.getDeadLetter();
        String deadLetterUri = definition.getDeadLetterUri();
        if (deadLetter == null) {
            StringHelper.notEmpty(deadLetterUri, "deadLetterUri", this);
            deadLetter = routeContext.getCamelContext().getEndpoint(deadLetterUri);
            if (deadLetter == null) {
                throw new NoSuchEndpointException(deadLetterUri);
            }
            // TODO: ErrorHandler: no modification to the model should be done
            definition.setDeadLetter(deadLetter);
        }
    }

}
