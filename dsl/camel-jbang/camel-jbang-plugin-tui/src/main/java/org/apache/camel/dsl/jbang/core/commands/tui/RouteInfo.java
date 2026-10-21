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
import java.util.List;
import java.util.Map;

class RouteInfo {
    String routeId;
    String description;
    /** Where the runtime loaded the route from (a file, classpath resource or jar entry, with the line). */
    String source;
    String group;
    String from;
    String state;
    boolean supportsSuspension;
    String uptime;
    String throughput;
    String coverage;
    long total;
    long failed;
    long inflight;
    long meanTime;
    long minTime;
    long maxTime;
    long lastTime;
    long deltaTime;
    long p50Time = -1;
    long p95Time = -1;
    long p99Time = -1;
    String load01;
    String load05;
    String load15;
    String sinceLastStarted;
    String sinceLastCompleted;
    String sinceLastFailed;
    /** The statistics as the status file has them, for the route diagram (DiagramTopology). */
    Map<String, ?> statistics;
    /** The steps reached with more than one body type, across the messages (CAMEL-25750). */
    int variedBodyTypes;
    /** The steps reached with bodies of more than one kind, which need attention (CAMEL-25750). */
    int mixedBodyKinds;
    final List<ProcessorInfo> processors = new ArrayList<>();
}
