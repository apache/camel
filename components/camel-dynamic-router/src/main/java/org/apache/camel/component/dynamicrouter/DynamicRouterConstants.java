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

/**
 * Contains constants that are used within this component.
 */
public abstract class DynamicRouterConstants {

    /**
     * The camel version where this router became dynamic.
     */
    public static final String FIRST_VERSION = "3.15.0";

    /**
     * The component name/scheme for the {@link DynamicRouterComponent}.
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
     * The syntax, for the auto-generated documentation.
     */
    public static final String SYNTAX = COMPONENT_SCHEME + ":channel";
}
