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

import java.util.Map;

class HistoryEntry {
    String pid;
    String exchangeId;
    String timestamp;
    String routeId;
    String fromRouteId;
    String nodeId;
    String nodeShortName;
    String nodeLabel;
    String location;
    String processor;
    String direction;
    String threadName;
    boolean first;
    boolean last;
    boolean failed;
    boolean remoteEndpoint;
    boolean stubEndpoint;
    int nodeLevel;
    long elapsed;
    long epochMs;
    String body;
    String bodyType;
    String exception;
    Map<String, Object> headers;
    Map<String, String> headerTypes;
    Map<String, Object> exchangeProperties;
    Map<String, String> exchangePropertyTypes;
    Map<String, Object> exchangeVariables;
    Map<String, String> exchangeVariableTypes;
}
