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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ActivityEntry {
    long uid;
    String exchangeId;
    String routeId;
    String fromRouteId;
    String fromEndpointUri;
    long timestamp;
    long elapsed;
    boolean failed;
    String exceptionType;
    String exceptionMessage;
    String stackTrace;
    String body;
    String bodyType;
    final List<EndpointSendEntry> endpointSends = new ArrayList<>();
    final Map<String, Object> headers = new LinkedHashMap<>();
    final Map<String, String> headerTypes = new LinkedHashMap<>();
    final Map<String, Object> properties = new LinkedHashMap<>();
    final Map<String, String> propertyTypes = new LinkedHashMap<>();
    final Map<String, Object> variables = new LinkedHashMap<>();
    final Map<String, String> variableTypes = new LinkedHashMap<>();

    static class EndpointSendEntry {
        String endpointUri;
        boolean remoteEndpoint;
        long elapsed;
    }
}
