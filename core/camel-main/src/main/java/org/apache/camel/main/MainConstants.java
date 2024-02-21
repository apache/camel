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
package org.apache.camel.main;

public final class MainConstants {

    public static final String DEFAULT_PROPERTY_PLACEHOLDER_LOCATION = "classpath:application.properties;optional=true";
    public static final String INITIAL_PROPERTIES_LOCATION = "camel.main.initial-properties-location";
    public static final String OVERRIDE_PROPERTIES_LOCATION = "camel.main.override-properties-location";
    public static final String PROPERTY_PLACEHOLDER_LOCATION = "camel.main.property-placeholder-location";
    public static final String PLATFORM_HTTP_SERVER = "platform-http-server";

    private MainConstants() {
    }

}
