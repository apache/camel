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
import java.util.UUID;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage.SubscribeMessageBuilder;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage.UnsubscribeMessageBuilder;
import org.apache.camel.spi.Language;
import org.apache.camel.support.AsyncProcessorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_ACTION_SUBSCRIBE;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_ACTION_UNSUBSCRIBE;

/**
 * Processes {@link DynamicRouterControlMessage}s on the specialized control channel.
 */
public class DynamicRouterControlChannelProcessor extends AsyncProcessorSupport {

    /**
     * The logger for instances to log messages.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterControlChannelProcessor.class);

    /**
     * The configuration for the Dynamic Router.
     */
    private DynamicRouterConfiguration configuration;

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
     * Tries to obtain the subscription {@link Predicate}. First, it looks for an expression from the URI parameters. If
     * that is not available, it checks the message body for the {@link Predicate}.
     *
     * @param  body body to inspect for a {@link Predicate} if the URI parameters does not have an expression
     * @return      the {@link Predicate}
     */
    Predicate obtainPredicate(final Object body) {
        Predicate predicate;
        final String exLang = configuration.getExpressionLanguage();
        final String value = configuration.getPredicate();
        final Predicate bean = configuration.getPredicateBean();
        if (bean != null) {
            predicate = bean;
        } else if (value != null && !value.isEmpty() && exLang != null && !exLang.isEmpty()) {
            try {
                Language language = component.getCamelContext().resolveLanguage(exLang);
                predicate = language.createPredicate(value);
            } catch (Exception e) {
                String message = String.format(
                        "Language '%s' and predicate expression '%s' could not create a valid predicate", exLang, value);
                throw new IllegalArgumentException(message, e);
            }
        } else {
            if (Predicate.class.isAssignableFrom(body.getClass())) {
                predicate = (Predicate) body;
            } else {
                throw new IllegalArgumentException(
                        "Subscription predicate must be provided either by an expression in" +
                                                   "the URI, as the message body, or as a property in a control channel" +
                                                   "message");
            }
        }
        return predicate;
    }

    /**
     * Process the {@link Exchange} for a {@link DynamicRouterControlMessage}, either as URI parameters, or as the
     * message body. If the control parameters are specified in the URI, then a {@link DynamicRouterControlMessage} is
     * created from the values.
     *
     * @param  exchange the {@link Exchange} to process
     * @return          the {@link DynamicRouterControlMessage}
     */
    DynamicRouterControlMessage handleControlMessage(final Exchange exchange) {
        final String controlAction = configuration.getControlAction();
        DynamicRouterControlMessage controlMessage;
        final Object body = exchange.getIn().getBody();
        if (controlAction != null && !controlAction.isEmpty()) {
            switch (controlAction) {
                case CONTROL_ACTION_UNSUBSCRIBE:
                    controlMessage = new UnsubscribeMessageBuilder()
                            .channel(configuration.getSubscribeChannel())
                            .id(configuration.getSubscriptionId())
                            .build();
                    break;
                case CONTROL_ACTION_SUBSCRIBE:
                    final String subscriptionId = configuration.getSubscriptionId() == null
                            ? UUID.randomUUID().toString() : configuration.getSubscriptionId();
                    controlMessage = new SubscribeMessageBuilder()
                            .channel(configuration.getSubscribeChannel())
                            .id(subscriptionId)
                            .endpointUri(configuration.getDestinationUri())
                            .priority(configuration.getPriority())
                            .predicate(obtainPredicate(body))
                            .build();
                    break;
                default:
                    throw new IllegalArgumentException("Illegal control channel action: " + controlAction);
            }
        } else if (DynamicRouterControlMessage.class.isAssignableFrom(body.getClass())) {
            controlMessage = (DynamicRouterControlMessage) body;
        } else {
            throw new IllegalArgumentException("Could not create or find a control channel message");
        }
        return controlMessage;
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
        DynamicRouterControlMessage controlMessage = handleControlMessage(exchange);
        final DynamicRouterProcessor processor = Optional.ofNullable(component.getRoutingProcessor(controlMessage.getChannel()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Control channel message is invalid: wrong channel, or no processors present."));
        switch (controlMessage.getMessageType()) {
            case SUBSCRIBE:
                processor.addFilter(controlMessage);
                exchange.getIn().setBody(controlMessage.getId(), String.class);
                break;
            case UNSUBSCRIBE:
                processor.removeFilter(controlMessage.getId());
                break;
            default:
                // Cannot get here due to enum
                break;
        }
        callback.done(true);
        return true;
    }

    /**
     * Set the {@link DynamicRouterConfiguration}.
     *
     * @param configuration the {@link DynamicRouterConfiguration}
     */
    public void setConfiguration(final DynamicRouterConfiguration configuration) {
        this.configuration = configuration;
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
