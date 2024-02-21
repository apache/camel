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
package org.apache.camel.component.dynamicrouter.routing;

import java.util.function.BiFunction;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.support.AsyncProcessorSupport;

import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.MODE_FIRST_MATCH;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.RECIPIENT_LIST_HEADER;

/**
 * The {@link DynamicRouterProcessor} is responsible for routing an exchange to the appropriate recipients. It uses the
 * {@link DynamicRouterFilterService} to determine which filters match the exchange, and then sets the recipient list
 * header on the exchange to the list of matching filters. The {@link RecipientList} processor is then used to route the
 * exchange to the matching recipients.
 */
public class DynamicRouterProcessor extends AsyncProcessorSupport {

    /**
     * Flag from the configuration to indicate if a dropped message should be logged at the 'WARN' level or not. If not,
     * it will be logged at the 'DEBUG' level.
     */
    private final boolean warnDroppedMessage;

    /**
     * The channel of the Dynamic Router that this processor services.
     */
    private final String channel;

    /**
     * Indicates the behavior of the Dynamic Router when routing participants are selected to receive an incoming
     * exchange. If the mode is "firstMatch", then the exchange is routed only to the first participant that has a
     * matching predicate. If the mode is "allMatch", then the exchange is routed to all participants that have a
     * matching predicate.
     */
    private final String recipientMode;

    /**
     * The recipient list processor.
     */
    private final RecipientList recipientList;

    /**
     * Service that manages {@link PrioritizedFilter}s for dynamic router channels.
     */
    private final DynamicRouterFilterService filterService;

    /**
     * Construct the {@link DynamicRouterProcessor} instance.
     *
     * @param recipientMode      the recipient mode
     * @param warnDroppedMessage flag from the configuration to indicate if a dropped message should be logged at the
     *                           'WARN' level
     * @param channel            the channel of the Dynamic Router that this processor services
     * @param recipientList      the recipient list processor
     * @param filterService      service that manages {@link PrioritizedFilter}s for dynamic router channels
     */
    public DynamicRouterProcessor(String recipientMode, boolean warnDroppedMessage, String channel,
                                  RecipientList recipientList, DynamicRouterFilterService filterService) {
        this.recipientMode = recipientMode;
        this.warnDroppedMessage = warnDroppedMessage;
        this.channel = channel;
        this.recipientList = recipientList;
        this.filterService = filterService;
    }

    /**
     * Match the exchange against all filters to determine if any of them are suitable to handle the exchange.
     *
     * @param  exchange the message exchange
     * @return          list of filters that match for the exchange; if "firstMatch" mode, it is a singleton list of
     *                  that filter
     */
    protected String matchFilters(final Exchange exchange) {
        return filterService.getMatchingEndpointsForExchangeByChannel(
                exchange, channel, MODE_FIRST_MATCH.equals(recipientMode), warnDroppedMessage);
    }

    /**
     * Prepare the exchange for processing by matching the exchange against the filters, and setting the recipient list
     * based on matching filter URIs.
     *
     * @param exchange the message exchange
     */
    public void prepareExchange(Exchange exchange) {
        Message message = exchange.getMessage();
        String recipients = matchFilters(exchange);
        message.setHeader(RECIPIENT_LIST_HEADER, recipients);
    }

    /**
     * Process the exchange asynchronously. The underlying RecipientList will handle the processing, including a check
     * to see if the exchange should be processed synchronously or asynchronously.
     *
     * @param exchange the exchange to process
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        prepareExchange(exchange);
        recipientList.process(exchange);
    }

    /**
     * Process the exchange, and use the {@link AsyncCallback} to signal completion.
     *
     * @param  exchange the exchange to process
     * @param  callback the {@link AsyncCallback} to signal when asynchronous processing has completed
     * @return          true to continue to execute synchronously, or false to continue to execute asynchronously
     */
    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        prepareExchange(exchange);
        return recipientList.process(exchange, callback);
    }

    /**
     * Factory for creating {@link DynamicRouterProcessor} instances.
     */
    public static class DynamicRouterProcessorFactory {

        /**
         * Create the {@link DynamicRouterProcessor} instance. This uses the {@link DynamicRouterConfiguration} to
         * create a {@link RecipientList} instance for the {@link DynamicRouterProcessor} to use for routing the
         * exchange to matching recipients.
         *
         * @param  camelContext          the CamelContext
         * @param  configuration         the configuration
         * @param  filterService         service that manages {@link PrioritizedFilter}s for dynamic router channels
         * @param  recipientListSupplier the supplier for the {@link RecipientList} instance
         * @return                       the {@link DynamicRouterProcessor} instance
         */
        public DynamicRouterProcessor getInstance(
                CamelContext camelContext, DynamicRouterConfiguration configuration,
                DynamicRouterFilterService filterService,
                BiFunction<CamelContext, Expression, RecipientList> recipientListSupplier) {
            RecipientList recipientList = (RecipientList) DynamicRouterRecipientListHelper
                    .createProcessor(camelContext, configuration, recipientListSupplier);
            return new DynamicRouterProcessor(
                    configuration.getRecipientMode(), configuration.isWarnDroppedMessage(),
                    configuration.getChannel(), recipientList, filterService);
        }
    }
}
