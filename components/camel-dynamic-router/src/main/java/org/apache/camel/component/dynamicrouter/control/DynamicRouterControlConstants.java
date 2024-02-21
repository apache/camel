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
package org.apache.camel.component.dynamicrouter.control;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Predicate;
import org.apache.camel.component.dynamicrouter.control.DynamicRouterControlEndpoint.DynamicRouterControlEndpointFactory;
import org.apache.camel.component.dynamicrouter.control.DynamicRouterControlProducer.DynamicRouterControlProducerFactory;
import org.apache.camel.component.dynamicrouter.control.DynamicRouterControlService.DynamicRouterControlServiceFactory;
import org.apache.camel.spi.Metadata;

/**
 * Constants pertaining to the Dynamic Router Control operations.
 */
public final class DynamicRouterControlConstants {

    private DynamicRouterControlConstants() {
    }

    /**
     * The camel version where the dynamic router control channel endpoint was introduced.
     */
    public static final String FIRST_VERSION_CONTROL = "4.4.0";

    /**
     * The component name/scheme for the {@link DynamicRouterControlEndpoint}.
     */
    public static final String COMPONENT_SCHEME_CONTROL = "dynamic-router-control";

    /**
     * The regex pattern for the control channel URI.
     */
    public static final Pattern CONTROL_URI_BASE_PATTERN = Pattern.compile("^" + COMPONENT_SCHEME_CONTROL + ":");

    /**
     * Function to match the control channel URI.
     */
    public static final Function<String, Matcher> OPTIMIZE_MATCHER = CONTROL_URI_BASE_PATTERN::matcher;

    /**
     * Function to determine if the control channel URI should be optimized.
     */
    public static final java.util.function.Predicate<String> SHOULD_OPTIMIZE = uri -> OPTIMIZE_MATCHER.apply(uri).find();

    /**
     * Convenient constant for the control channel URI.
     */
    public static final String CONTROL_CHANNEL_URI = COMPONENT_SCHEME_CONTROL;

    /**
     * The title of the dynamic router control endpoint, for the auto-generated documentation.
     */
    public static final String TITLE_CONTROL = "Dynamic Router Control";

    /**
     * The syntax of the control endpoint, for the auto-generated documentation.
     */
    public static final String SYNTAX_CONTROL = COMPONENT_SCHEME_CONTROL + ":controlAction";

    /**
     * Subscribe control channel action.
     */
    public static final String CONTROL_ACTION_SUBSCRIBE = "subscribe";

    /**
     * Unsubscribe control channel action.
     */
    public static final String CONTROL_ACTION_UNSUBSCRIBE = "unsubscribe";

    /**
     * Update control channel action.
     */
    public static final String CONTROL_ACTION_UPDATE = "update";

    /**
     * Subscription list control channel action.
     */
    public static final String CONTROL_ACTION_LIST = "list";

    /**
     * Routing statistics control channel action.
     */
    public static final String CONTROL_ACTION_STATS = "statistics";

    /**
     * The name of the "simple" language.
     */
    public static final String SIMPLE_LANGUAGE = "simple";

    /**
     * Error string when the message body is assumed to contain a {@link Predicate}, but the body's class is not
     * resolvable to {@link Predicate}.
     */
    public static final String ERROR_PREDICATE_CLASS = "To supply a predicate in the message body, the body's class " +
                                                       "must be resolvable to " + Predicate.class.getCanonicalName();

    /**
     * Error when the specified expression language and the predicate expression cannot produce a predicate.
     */
    public static final String ERROR_INVALID_PREDICATE_EXPRESSION
            = "Language '%s' and predicate expression '%s' could not create a valid predicate";

    /**
     * Error when a predicate bean cannot be found.
     */
    public static final String ERROR_NO_PREDICATE_BEAN_FOUND = "Predicate bean could not be found";

    /**
     * The configuration property for the control channel action.
     */
    public static final String CONTROL_ACTION_PROPERTY = "controlAction";

    /**
     * The configuration property for the subscribe channel.
     */
    public static final String SUBSCRIBE_CHANNEL_PROPERTY = "subscribeChannel";

    /**
     * The configuration property for the subscription ID.
     */
    public static final String SUBSCRIPTION_ID_PROPERTY = "subscriptionId";

    /**
     * The configuration property for the destination URI.
     */
    public static final String DESTINATION_URI_PROPERTY = "destinationUri";

    /**
     * The configuration property for the filter priority.
     */
    public static final String PRIORITY_PROPERTY = "priority";

    /**
     * The configuration property for the filter predicate.
     */
    public static final String PREDICATE_PROPERTY = "predicate";

    /**
     * The configuration property for a predicate bean in the registry (for a filter).
     */
    public static final String PREDICATE_BEAN_PROPERTY = "predicateBean";

    /**
     * The configuration property for the predicate expression language.
     */
    public static final String EXPRESSION_LANGUAGE_PROPERTY = "expressionLanguage";

    /**
     * Header name for the control action.
     */
    @Metadata(description = "The control action header.", javaType = "String")
    public static final String CONTROL_ACTION_HEADER = "CamelDynamicRouterControlAction";

    /**
     * Header name for the subscribe channel.
     */
    @Metadata(description = "The Dynamic Router channel that the subscriber is subscribing on.",
              javaType = "String")
    public static final String CONTROL_SUBSCRIBE_CHANNEL = "CamelDynamicRouterSubscribeChannel";

    /**
     * Header name for the subscription ID.
     */
    @Metadata(description = "The subscription ID.",
              javaType = "String")
    public static final String CONTROL_SUBSCRIPTION_ID = "CamelDynamicRouterSubscriptionId";

    /**
     * Header name for the destination URI.
     */
    @Metadata(description = "The URI on which the routing participant wants to receive matching exchanges.",
              javaType = "String")
    public static final String CONTROL_DESTINATION_URI = "CamelDynamicRouterDestinationUri";

    /**
     * Header name for the routing priority.
     */
    @Metadata(description = "The priority of this subscription",
              javaType = "String")
    public static final String CONTROL_PRIORITY = "CamelDynamicRouterPriority";

    /**
     * Header name for the predicate.
     */
    @Metadata(description = "The predicate to evaluate exchanges for this subscription",
              javaType = "String")
    public static final String CONTROL_PREDICATE = "CamelDynamicRouterPredicate";

    /**
     * Header name for the predicate bean reference.
     */
    @Metadata(description = "The name of the bean in the registry that identifies the subscription predicate.",
              javaType = "String")
    public static final String CONTROL_PREDICATE_BEAN = "CamelDynamicRouterPredicateBean";

    /**
     * Header name for the predicate expression language.
     */
    @Metadata(description = "The language for the predicate when supplied as a string.",
              javaType = "String")
    public static final String CONTROL_EXPRESSION_LANGUAGE = "CamelDynamicRouterExpressionLanguage";

    /**
     * Map of control channel URI parameters to header names.
     */
    public static final Map<String, String> URI_PARAMS_TO_HEADER_NAMES = Map.of(
            CONTROL_ACTION_PROPERTY, CONTROL_ACTION_HEADER,
            SUBSCRIBE_CHANNEL_PROPERTY, CONTROL_SUBSCRIBE_CHANNEL,
            SUBSCRIPTION_ID_PROPERTY, CONTROL_SUBSCRIPTION_ID,
            DESTINATION_URI_PROPERTY, CONTROL_DESTINATION_URI,
            PRIORITY_PROPERTY, CONTROL_PRIORITY,
            PREDICATE_PROPERTY, CONTROL_PREDICATE,
            PREDICATE_BEAN_PROPERTY, CONTROL_PREDICATE_BEAN,
            EXPRESSION_LANGUAGE_PROPERTY, CONTROL_EXPRESSION_LANGUAGE);

    /**
     * The supplier for the control endpoint factory.
     */
    public static final Supplier<DynamicRouterControlEndpointFactory> CONTROL_ENDPOINT_FACTORY_SUPPLIER
            = DynamicRouterControlEndpointFactory::new;

    /**
     * The supplier for the producer factory.
     */
    public static final Supplier<DynamicRouterControlProducerFactory> CONTROL_PRODUCER_FACTORY_SUPPLIER
            = DynamicRouterControlProducerFactory::new;

    /**
     * The supplier for the control service factory.
     */
    public static final Supplier<DynamicRouterControlServiceFactory> CONTROL_SERVICE_FACTORY_SUPPLIER
            = DynamicRouterControlServiceFactory::new;
}
