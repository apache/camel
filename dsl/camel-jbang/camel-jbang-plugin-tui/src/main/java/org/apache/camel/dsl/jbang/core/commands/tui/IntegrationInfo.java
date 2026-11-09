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
import java.util.Set;

class IntegrationInfo {

    /** A kind of error that happened: in a route, of an exception type, this many times. */
    record ErrorKind(String routeId, String exceptionType, long count) {
    }

    String pid;
    String name;
    String camelVersion;
    String platform;
    String platformVersion;
    String javaVersion;
    String javaVendor;
    String javaVmName;
    String directory;
    String profile;
    boolean devMode;
    boolean phantom;
    // an opened project (phantom): the pid of its running app while it runs
    String linkedPid;
    // a running app: the name of the project it was opened as, when it differs from the app's own name
    String openedAs;
    // an opened project: when it was asked to run (a MonotonicClock time), until its app shows up (or the launch fails)
    long startingSince;
    // the process F10 launched for an opened project: its app is that process or a descendant of it (CAMEL-25417)
    ProcessHandle launchedProcess;
    String projectType;
    String sourceDir;
    String ready;
    int state;
    long uptime;
    // when the CamelContext started (epoch millis), after it loaded its routes; 0 when the integration does not say
    long contextStarted;
    String ago;
    String throughput;
    long exchangesTotal;
    long failed;
    long inflight;
    String inflightLoad01;
    String inflightLoad05;
    String inflightLoad15;
    long meanTime;
    long maxTime;
    long minTime;
    long lastTime;
    long deltaTime;
    long p50Time = -1;
    long p95Time = -1;
    long p99Time = -1;
    String last;
    String delta;
    String sinceLastStarted;
    String sinceLastCompleted;
    String sinceLastFailed;
    int reloaded;
    // why the last reload failed (dev mode), until a reload succeeds again; camel ps shows it as Error
    String reloadError;
    String rootLogLevel;
    int routeStarted;
    int routeTotal;
    long heapMemUsed;
    long heapMemCommitted;
    long heapMemMax;
    long nonHeapMemUsed;
    long nonHeapMemCommitted;
    long oldGenUsed;
    long oldGenCommitted;
    long oldGenMax;
    long metaspaceUsed;
    long metaspaceCommitted;
    long metaspaceMax;
    long gcCollectionCount;
    long gcCollectionTime;
    int loadedClassCount;
    long totalLoadedClassCount;
    int threadCount;
    int peakThreadCount;
    boolean vanishing;
    // when it last wrote its status file (epoch millis), 0 when not known; StatusAge tells one that stopped writing it
    long statusWrittenAt;
    // while it writes no status: the CPU time of its process, when it was read (a MonotonicClock time), and the CPU
    // since the read before in percent of one core (-1 not known yet)
    long cpuMillis = -1;
    long cpuReadAt;
    double cpuPercent = -1;
    // when it was found gone, a MonotonicClock time: it fades out from then
    long vanishStart;
    final List<RouteInfo> routes = new ArrayList<>();
    final List<ConsumerInfo> consumers = new ArrayList<>();
    final List<ProducerInfo> producers = new ArrayList<>();
    final List<EventInfo> events = new ArrayList<>();
    String routeControllerType;
    boolean routeControllerUnhealthy;
    int routeControllerRestartingRoutes;
    int routeControllerExhaustedRoutes;
    final List<RouteControllerInfo> routeControllerRoutes = new ArrayList<>();
    final List<HealthCheckInfo> healthChecks = new ArrayList<>();
    // whether the integration is ready (may take traffic) and live (runs), as its health checks say; null when not known
    Boolean healthReady;
    Boolean healthLive;
    final List<EndpointInfo> endpoints = new ArrayList<>();
    final List<ServiceInfo> services = new ArrayList<>();
    final List<CircuitBreakerInfo> circuitBreakers = new ArrayList<>();
    // the chaos rules and report (camel-chaos), or null without camel-chaos
    ChaosInfo chaos;
    final List<KafkaConsumerInfo> kafkaConsumers = new ArrayList<>();
    // the errors that happened (the registry keeps only a few entries of each kind), and the kinds that happened most
    int errorCount;
    // when the newest error happened (epoch millis), 0 when the integration does not say
    long lastErrorTimestamp;
    volatile List<ErrorKind> errorKinds = List.of();
    volatile List<ErrorInfo> errors = new ArrayList<>();
    boolean inflightBrowseEnabled;
    final List<InflightInfo> inflightExchanges = new ArrayList<>();
    final List<MicrometerMeterInfo> meters = new ArrayList<>();
    final List<HttpEndpointInfo> httpEndpoints = new ArrayList<>();
    final List<ConfigurationTab.ConfigProperty> configProperties = new ArrayList<>();
    final List<InternalTaskInfo> internalTasks = new ArrayList<>();
    final List<DataSourceInfo> dataSources = new ArrayList<>();
    final List<SqlTraceInfo> sqlTraceStatements = new ArrayList<>();
    long sqlTraceTotal;
    long sqlTraceAvgTime;
    long sqlTraceSlowestTime;
    long sqlTraceSlowCount;
    long sqlTraceFailedCount;
    String httpServer;
    String readmeFiles;
    Set<String> devConsoles = Set.of();
    boolean hasBrowseableEndpoints;
    int transformerCount;
    final List<VaultSecretInfo> vaultSecrets = new ArrayList<>();
}
