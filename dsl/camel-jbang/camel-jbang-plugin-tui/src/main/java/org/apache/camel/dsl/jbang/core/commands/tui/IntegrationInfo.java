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

class IntegrationInfo {
    String pid;
    String name;
    String camelVersion;
    String platform;
    String platformVersion;
    String javaVersion;
    String javaVendor;
    String javaVmName;
    String profile;
    boolean devMode;
    String ready;
    int state;
    long uptime;
    String ago;
    String throughput;
    long exchangesTotal;
    long failed;
    long inflight;
    String inflightLoad01;
    String inflightLoad05;
    String inflightLoad15;
    String last;
    String delta;
    String sinceLastStarted;
    String sinceLastCompleted;
    String sinceLastFailed;
    int reloaded;
    String rootLogLevel;
    int routeStarted;
    int routeTotal;
    long heapMemUsed;
    long heapMemMax;
    long nonHeapMemUsed;
    int threadCount;
    int peakThreadCount;
    boolean vanishing;
    long vanishStart;
    final List<RouteInfo> routes = new ArrayList<>();
    final List<ConsumerInfo> consumers = new ArrayList<>();
    final List<HealthCheckInfo> healthChecks = new ArrayList<>();
    final List<EndpointInfo> endpoints = new ArrayList<>();
    final List<CircuitBreakerInfo> circuitBreakers = new ArrayList<>();
    final List<ErrorInfo> errors = new ArrayList<>();
    final List<HttpEndpointInfo> httpEndpoints = new ArrayList<>();
    String httpServer;
    String readmeFiles;
}
