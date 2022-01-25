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
package org.apache.camel.component.dynamicrouter;

import java.util.Optional;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.dynamicrouter.message.DynamicRouterControlMessage;
import org.apache.camel.component.dynamicrouter.processor.DynamicRouterProcessor;
import org.apache.camel.support.AsyncProcessorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes {@link DynamicRouterControlMessage}s on the specialized control channel.
 */
public class DynamicRouterControlChannelProcessor extends AsyncProcessorSupport {

    /**
     * The logger for instances to log messages.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterControlChannelProcessor.class);

    /**
     * The {@link DynamicRouterComponent} that this instance processes {@link DynamicRouterControlMessage}s for.
     */
    private final DynamicRouterComponent component;

    /**
     * Create the instance to process {@link DynamicRouterControlMessage}s for the supplied component.
     *
     * @param component the {@link DynamicRouterComponent} that this instance processes
     *                  {@link DynamicRouterControlMessage}s for
     */
    public DynamicRouterControlChannelProcessor(final DynamicRouterComponent component) {
        this.component = component;
    }

    /**
     * When a {@link DynamicRouterControlMessage} is received, it is processed, depending on the
     * {@link DynamicRouterControlMessage#getMessageType()}: if the type is
     * {@link DynamicRouterControlMessage.ControlMessageType#SUBSCRIBE}, then create the
     * {@link org.apache.camel.processor.FilterProcessor} and add it to the consumer's filters, but if the type is
     * {@link DynamicRouterControlMessage.ControlMessageType#UNSUBSCRIBE}, then the entry for the endpoint is removed.
     *
     * @param  exchange the exchange, where the body should be a {@link DynamicRouterControlMessage}
     * @param  callback the {@link AsyncCallback}
     * @return          true, always, because these messages are only consumed, so we do not need to continue
     *                  asynchronously
     */
    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        LOG.debug("Received control channel message");
        final DynamicRouterControlMessage controlMessage = exchange.getIn().getBody(DynamicRouterControlMessage.class);
        final DynamicRouterProcessor processor = Optional.ofNullable(component.getConsumer(controlMessage.getChannel()))
                .map(DynamicRouterConsumer::getProcessor)
                .map(DynamicRouterProcessor.class::cast)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Control channel message is invalid: wrong channel, or no processors present."));
        switch (controlMessage.getMessageType()) {
            case SUBSCRIBE:
                processor.addFilter(controlMessage);
                break;
            case UNSUBSCRIBE:
                processor.removeFilter(controlMessage.getId());
                break;
            default:
                throw new IllegalArgumentException("You can only subscribe or unsubscribe for dynamic routing");
        }
        callback.done(true);
        return true;
    }

    /**
     * Create a {@link DynamicRouterControlChannelProcessor} instance.
     */
    public static class DynamicRouterControlChannelProcessorFactory {

        /**
         * Create the {@link DynamicRouterControlChannelProcessor} instance for the {@link DynamicRouterComponent}.
         *
         * @param dynamicRouterComponent the {@link DynamicRouterComponent} to handle control messages for
         */
        public DynamicRouterControlChannelProcessor getInstance(final DynamicRouterComponent dynamicRouterComponent) {
            return new DynamicRouterControlChannelProcessor(dynamicRouterComponent);
        }
    }
}
