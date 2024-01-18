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

import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService.DynamicRouterFilterServiceFactory;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter.PrioritizedFilterFactory;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterEndpoint.DynamicRouterEndpointFactory;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterProcessor.DynamicRouterProcessorFactory;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.support.builder.ExpressionBuilder;

/**
 * Contains constants that are used within this component.
 */
public final class DynamicRouterConstants {

    private DynamicRouterConstants() {
    }

    /**
     * The camel version where the dynamic router eip component was first introduced.
     */
    public static final String FIRST_VERSION = "3.15.0";

    /**
     * The component name/scheme for the {@link DynamicRouterEndpoint}.
     */
    public static final String COMPONENT_SCHEME_ROUTING = "dynamic-router";

    /**
     * The title, for the auto-generated documentation.
     */
    public static final String TITLE = "Dynamic Router";

    /**
     * The mode for sending an exchange to recipients: send only to the first match.
     */
    public static final String MODE_FIRST_MATCH = "firstMatch";

    /**
     * The mode for sending an exchange to recipients: send to all matching.
     */
    public static final String MODE_ALL_MATCH = "allMatch";

    /**
     * The syntax, for the auto-generated documentation.
     */
    public static final String SYNTAX = COMPONENT_SCHEME_ROUTING + ":channel";

    /**
     * The name of the header that stores the original message body when no matching filters can be found.
     */
    public static final String ORIGINAL_BODY_HEADER = "originalBody";

    /**
     * The name of the header that holds the destination URIs for the recipients.
     */
    public static final String RECIPIENT_LIST_HEADER = "DynamicRouterRecipientList";

    /**
     * The {@link Expression} that is used to configure the recipient list to get recipient URIs from the
     * {@link #RECIPIENT_LIST_HEADER}.
     */
    public static final Expression RECIPIENT_LIST_EXPRESSION = ExpressionBuilder.headerExpression(RECIPIENT_LIST_HEADER);

    /**
     * Creates a {@link DynamicRouterEndpoint} instance.
     */
    public static final Supplier<DynamicRouterEndpointFactory> ENDPOINT_FACTORY_SUPPLIER = DynamicRouterEndpointFactory::new;

    /**
     * Creates a {@link DynamicRouterProcessor} instance.
     */
    public static final Supplier<DynamicRouterProcessorFactory> PROCESSOR_FACTORY_SUPPLIER = DynamicRouterProcessorFactory::new;

    /**
     * Creates a {@link DynamicRouterProducer} instance.
     */
    public static final Supplier<DynamicRouterProducerFactory> PRODUCER_FACTORY_SUPPLIER = DynamicRouterProducerFactory::new;

    /**
     * Creates a {@link PrioritizedFilter} instance.
     */
    public static final Supplier<PrioritizedFilterFactory> FILTER_FACTORY_SUPPLIER = PrioritizedFilterFactory::new;

    /**
     * Creates a {@link DynamicRouterFilterService} instance.
     */
    public static final Supplier<DynamicRouterFilterServiceFactory> FILTER_SERVICE_FACTORY_SUPPLIER
            = DynamicRouterFilterServiceFactory::new;

    /**
     * A comparator to sort {@link PrioritizedFilter}s by their priority field.
     */
    public static final Comparator<PrioritizedFilter> FILTER_COMPARATOR = Comparator
            .comparingInt(PrioritizedFilter::priority)
            .thenComparing(PrioritizedFilter::id);

    /**
     * Creates a {@link RecipientList} instance.
     */
    public static final BiFunction<CamelContext, Expression, RecipientList> RECIPIENT_LIST_SUPPLIER = RecipientList::new;

    /**
     * Template for a logging endpoint, showing all, and multiline.
     */
    public static final String LOG_ENDPOINT = "log:%s.%s?level=%s&showAll=true&multiline=true";
}
