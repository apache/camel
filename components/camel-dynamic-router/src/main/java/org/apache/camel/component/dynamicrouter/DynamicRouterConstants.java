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

import java.util.regex.Pattern;

/**
 * Contains constants that are used within this component.
 */
public final class DynamicRouterConstants {

    /**
     * The camel version where this router became dynamic.
     */
    public static final String FIRST_VERSION = "3.15.0";

    /**
     * The component name/scheme for the {@link DynamicRouterEndpoint}.
     */
    public static final String COMPONENT_SCHEME = "dynamic-router";

    /**
     * The control channel, where routing participants subscribe and provide their routing rules and endpoint URIs.
     */
    public static final String CONTROL_CHANNEL_NAME = "control";

    /**
     * Convenient constant for the control channel URI.
     */
    public static final String CONTROL_CHANNEL_URI = COMPONENT_SCHEME + ":" + CONTROL_CHANNEL_NAME;

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
    public static final String SYNTAX = COMPONENT_SCHEME + ":channel";

    /**
     * Name of the control action parameter.
     */
    public static final String CONTROL_ACTION_PARAM = "controlAction";

    /**
     * Name of the channel parameter.
     */
    public static final String SUBSCRIPTION_CHANNEL_PARAM = "subscribeChannel";

    /**
     * The alternate control-channel syntax.
     */
    public static final String CONTROL_SYNTAX
            = SYNTAX + "/" + CONTROL_ACTION_PARAM + "/" + SUBSCRIPTION_CHANNEL_PARAM;

    /**
     * Subscribe control channel action.
     */
    public static final String CONTROL_ACTION_SUBSCRIBE = "subscribe";

    /**
     * Unsubscribe control channel action.
     */
    public static final String CONTROL_ACTION_UNSUBSCRIBE = "unsubscribe";

    /**
     * The name for the regex capture group that captures the channel name.
     */
    public static final String CHANNEL_GROUP = "channel";

    /**
     * The name for the regex capture group that captures the control channel action.
     */
    public static final String ACTION_GROUP = "action";

    /**
     * The name for the regex capture group that captures the channel name for the subscription.
     */
    public static final String SUBSCRIBE_GROUP = "subscribe";

    /**
     * Regular expression to parse URI path parameters.
     */
    public static final Pattern PATH_PARAMS_PATTERN = Pattern.compile(
            String.format("(?<%s>[^/]+)(/(?<%s>[^/]+)/(?<%s>[^/]+))?", CHANNEL_GROUP, ACTION_GROUP, SUBSCRIBE_GROUP));

    private DynamicRouterConstants() {

    }
}
