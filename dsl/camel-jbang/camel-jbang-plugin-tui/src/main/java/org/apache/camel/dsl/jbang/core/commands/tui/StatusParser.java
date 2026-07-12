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

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

final class StatusParser {

    private StatusParser() {
    }

    record MessageData(
            Map<String, Object> headers,
            Map<String, String> headerTypes,
            String body,
            String bodyType,
            Map<String, Object> exchangeProperties,
            Map<String, String> exchangePropertyTypes,
            Map<String, Object> exchangeVariables,
            Map<String, String> exchangeVariableTypes) {
    }

    @SuppressWarnings("unchecked")
    static IntegrationInfo parseIntegration(ProcessHandle ph, JsonObject root) {
        JsonObject context = (JsonObject) root.get("context");
        if (context == null) {
            return null;
        }

        IntegrationInfo info = new IntegrationInfo();
        info.name = context.getString("name");
        if ("CamelJBang".equals(info.name)) {
            info.name = ProcessHelper.extractName(root, ph);
        }
        info.pid = Long.toString(ph.pid());
        info.uptime = extractSince(ph);
        info.ago = TimeUtils.printSince(info.uptime);
        info.state = context.getIntegerOrDefault("phase", 0);
        info.camelVersion = context.getString("version");
        info.profile = context.getString("profile");
        info.devMode = context.getBooleanOrDefault("devMode", false);

        JsonObject runtime = (JsonObject) root.get("runtime");
        info.platform = runtime != null ? runtime.getString("platform") : null;
        info.platformVersion = runtime != null ? runtime.getString("platformVersion") : null;
        if ("Camel".equals(info.platform)) {
            String cl = ph.info().commandLine().orElse("");
            if (cl.contains("main.CamelJBang run")) {
                info.platform = "JBang";
                if (info.platformVersion == null) {
                    info.platformVersion = VersionHelper.getJBangVersion();
                }
            }
        }
        info.directory = runtime != null ? runtime.getString("directory") : null;
        info.javaVersion = runtime != null ? runtime.getString("javaVersion") : null;
        info.javaVendor = runtime != null ? runtime.getString("javaVendor") : null;
        info.javaVmName = runtime != null ? runtime.getString("javaVmName") : null;
        info.readmeFiles = runtime != null ? runtime.getString("readmeFiles") : null;

        Map<String, ?> stats = context.getMap("statistics");
        if (stats != null) {
            Object thp = stats.get("exchangesThroughput");
            if (thp != null) {
                info.throughput = thp.toString();
            }
            info.exchangesTotal = objToLong(stats.get("exchangesTotal"));
            info.failed = objToLong(stats.get("exchangesFailed"));
            info.inflight = objToLong(stats.get("exchangesInflight"));
            info.inflightLoad01 = objToString(stats.get("load01"));
            info.inflightLoad05 = objToString(stats.get("load05"));
            info.inflightLoad15 = objToString(stats.get("load15"));
            info.meanTime = Math.max(0, objToLong(stats.get("meanProcessingTime")));
            info.maxTime = Math.max(0, objToLong(stats.get("maxProcessingTime")));
            info.minTime = Math.max(0, objToLong(stats.get("minProcessingTime")));
            info.p50Time = objToLong(stats.get("p50ProcessingTime"));
            info.p95Time = objToLong(stats.get("p95ProcessingTime"));
            info.p99Time = objToLong(stats.get("p99ProcessingTime"));
            info.last = objToString(stats.get("lastProcessingTime"));
            info.lastTime = Math.max(0, objToLong(stats.get("lastProcessingTime")));
            info.delta = objToString(stats.get("deltaProcessingTime"));
            info.deltaTime = objToLong(stats.get("deltaProcessingTime"));
            long tsStarted = objToLong(stats.get("lastCreatedExchangeTimestamp"));
            if (tsStarted > 0) {
                info.sinceLastStarted = TimeUtils.printSince(tsStarted);
            }
            long tsCompleted = objToLong(stats.get("lastCompletedExchangeTimestamp"));
            if (tsCompleted > 0) {
                info.sinceLastCompleted = TimeUtils.printSince(tsCompleted);
            }
            long tsFailed = objToLong(stats.get("lastFailedExchangeTimestamp"));
            if (tsFailed > 0) {
                info.sinceLastFailed = TimeUtils.printSince(tsFailed);
            }
            Map<String, ?> reloadStats = (Map<String, ?>) stats.get("reload");
            if (reloadStats != null) {
                info.reloaded = (int) objToLong(reloadStats.get("reloaded"));
            }
        }

        JsonObject mem = (JsonObject) root.get("memory");
        if (mem != null) {
            info.heapMemUsed = mem.getLongOrDefault("heapMemoryUsed", 0L);
            info.heapMemCommitted = mem.getLongOrDefault("heapMemoryCommitted", 0L);
            info.heapMemMax = mem.getLongOrDefault("heapMemoryMax", 0L);
            info.nonHeapMemUsed = mem.getLongOrDefault("nonHeapMemoryUsed", 0L);
            info.nonHeapMemCommitted = mem.getLongOrDefault("nonHeapMemoryCommitted", 0L);
            info.oldGenUsed = mem.getLongOrDefault("oldGenUsed", 0L);
            info.oldGenCommitted = mem.getLongOrDefault("oldGenCommitted", 0L);
            info.oldGenMax = mem.getLongOrDefault("oldGenMax", 0L);
            info.metaspaceUsed = mem.getLongOrDefault("metaspaceUsed", 0L);
            info.metaspaceCommitted = mem.getLongOrDefault("metaspaceCommitted", 0L);
            info.metaspaceMax = mem.getLongOrDefault("metaspaceMax", 0L);
        }

        JsonObject gc = (JsonObject) root.get("gc");
        if (gc != null) {
            info.gcCollectionCount = gc.getLongOrDefault("collectionCount", 0L);
            info.gcCollectionTime = gc.getLongOrDefault("collectionTime", 0L);
        }

        JsonObject classLoading = (JsonObject) root.get("classLoading");
        if (classLoading != null) {
            info.loadedClassCount = classLoading.getIntegerOrDefault("loadedClassCount", 0);
            info.totalLoadedClassCount = classLoading.getLongOrDefault("totalLoadedClassCount", 0L);
        }

        JsonObject threads = (JsonObject) root.get("threads");
        if (threads != null) {
            info.threadCount = threads.getIntegerOrDefault("threadCount", 0);
            info.peakThreadCount = threads.getIntegerOrDefault("peakThreadCount", 0);
        }

        JsonObject logger = (JsonObject) root.get("logger");
        if (logger != null) {
            JsonObject levels = (JsonObject) logger.get("levels");
            if (levels != null) {
                info.rootLogLevel = levels.getString("root");
            }
        }

        // Parse routes
        JsonArray routes = (JsonArray) root.get("routes");
        if (routes != null) {
            for (Object r : routes) {
                JsonObject rj = (JsonObject) r;
                RouteInfo ri = new RouteInfo();
                ri.routeId = rj.getString("routeId");
                ri.description = rj.getString("description");
                ri.group = rj.getString("group");
                ri.from = rj.getString("from");
                ri.state = rj.getString("state");
                ri.supportsSuspension = rj.getBooleanOrDefault("supportsSuspension", false);
                ri.uptime = rj.getString("uptime");

                Map<String, ?> rs = rj.getMap("statistics");
                if (rs != null) {
                    ri.coverage = objToString(rs.get("coverage"));
                    ri.throughput = objToString(rs.get("exchangesThroughput"));
                    ri.total = objToLong(rs.get("exchangesTotal"));
                    ri.failed = objToLong(rs.get("exchangesFailed"));
                    ri.inflight = objToLong(rs.get("exchangesInflight"));
                    ri.meanTime = Math.max(0, objToLong(rs.get("meanProcessingTime")));
                    ri.minTime = Math.max(0, objToLong(rs.get("minProcessingTime")));
                    ri.maxTime = Math.max(0, objToLong(rs.get("maxProcessingTime")));
                    ri.lastTime = Math.max(0, objToLong(rs.get("lastProcessingTime")));
                    ri.deltaTime = objToLong(rs.get("deltaProcessingTime"));
                    ri.p50Time = objToLong(rs.get("p50ProcessingTime"));
                    ri.p95Time = objToLong(rs.get("p95ProcessingTime"));
                    ri.p99Time = objToLong(rs.get("p99ProcessingTime"));
                    ri.load01 = objToString(rs.get("load01"));
                    ri.load05 = objToString(rs.get("load05"));
                    ri.load15 = objToString(rs.get("load15"));
                    long tsStarted = objToLong(rs.get("lastCreatedExchangeTimestamp"));
                    if (tsStarted > 0) {
                        ri.sinceLastStarted = TimeUtils.printSince(tsStarted);
                    }
                    long tsCompleted = objToLong(rs.get("lastCompletedExchangeTimestamp"));
                    if (tsCompleted > 0) {
                        ri.sinceLastCompleted = TimeUtils.printSince(tsCompleted);
                    }
                    long tsFailed = objToLong(rs.get("lastFailedExchangeTimestamp"));
                    if (tsFailed > 0) {
                        ri.sinceLastFailed = TimeUtils.printSince(tsFailed);
                    }
                }

                // Parse processors
                JsonArray procs = (JsonArray) rj.get("processors");
                if (procs != null) {
                    for (Object p : procs) {
                        JsonObject pj = (JsonObject) p;
                        ProcessorInfo pi = new ProcessorInfo();
                        pi.id = pj.getString("id");
                        pi.processor = pj.getString("processor");
                        pi.level = pj.getIntegerOrDefault("level", 0);

                        Map<String, ?> ps = pj.getMap("statistics");
                        if (ps != null) {
                            pi.total = objToLong(ps.get("exchangesTotal"));
                            pi.failed = objToLong(ps.get("exchangesFailed"));
                            pi.meanTime = Math.max(0, objToLong(ps.get("meanProcessingTime")));
                            pi.minTime = Math.max(0, objToLong(ps.get("minProcessingTime")));
                            pi.maxTime = Math.max(0, objToLong(ps.get("maxProcessingTime")));
                            pi.lastTime = objToLong(ps.get("lastProcessingTime"));
                            pi.deltaTime = objToLong(ps.get("deltaProcessingTime"));
                            pi.p50Time = objToLong(ps.get("p50ProcessingTime"));
                            pi.p95Time = objToLong(ps.get("p95ProcessingTime"));
                            pi.p99Time = objToLong(ps.get("p99ProcessingTime"));
                            pi.inflight = objToLong(ps.get("exchangesInflight"));
                            long tsStarted = objToLong(ps.get("lastCreatedExchangeTimestamp"));
                            if (tsStarted > 0) {
                                pi.sinceLastStarted = TimeUtils.printSince(tsStarted);
                            }
                            long tsCompleted = objToLong(ps.get("lastCompletedExchangeTimestamp"));
                            if (tsCompleted > 0) {
                                pi.sinceLastCompleted = TimeUtils.printSince(tsCompleted);
                            }
                            long tsFailed = objToLong(ps.get("lastFailedExchangeTimestamp"));
                            if (tsFailed > 0) {
                                pi.sinceLastFailed = TimeUtils.printSince(tsFailed);
                            }
                        }

                        ri.processors.add(pi);
                    }
                }

                info.routes.add(ri);
            }
            info.routeTotal = info.routes.size();
            info.routeStarted = (int) info.routes.stream().filter(r -> "Started".equals(r.state)).count();
        }

        // Parse health checks and ready status
        JsonObject healthChecks = (JsonObject) root.get("healthChecks");
        if (healthChecks != null) {
            Boolean rdy = (Boolean) healthChecks.get("ready");
            info.ready = Boolean.TRUE.equals(rdy) ? "1/1" : "0/1";
            JsonArray checks = (JsonArray) healthChecks.get("checks");
            if (checks != null) {
                for (Object c : checks) {
                    JsonObject cj = (JsonObject) c;
                    HealthCheckInfo hc = new HealthCheckInfo();
                    hc.group = cj.getString("group");
                    hc.name = cj.getString("id");
                    hc.state = cj.getString("state");
                    hc.readiness = cj.getBooleanOrDefault("readiness", false);
                    hc.liveness = cj.getBooleanOrDefault("liveness", false);
                    hc.message = cj.getString("message");
                    if (hc.message == null) {
                        JsonObject details = (JsonObject) cj.get("details");
                        if (details != null && details.containsKey("failure.error.message")) {
                            hc.message = details.getString("failure.error.message");
                        }
                    }
                    info.healthChecks.add(hc);
                }
            }
        }

        // Parse consumers
        JsonObject consumersObj = (JsonObject) root.get("consumers");
        if (consumersObj != null) {
            JsonArray consumerList = (JsonArray) consumersObj.get("consumers");
            if (consumerList != null) {
                for (Object c : consumerList) {
                    JsonObject cj = (JsonObject) c;
                    ConsumerInfo ci = new ConsumerInfo();
                    ci.id = cj.getString("id");
                    ci.uri = cj.getString("uri");
                    ci.state = cj.getString("state");
                    ci.className = cj.getString("class");
                    ci.scheduled = Boolean.TRUE.equals(cj.get("scheduled"));
                    ci.inflight = cj.getIntegerOrDefault("inflight", 0);
                    ci.polling = Boolean.TRUE.equals(cj.get("polling"));
                    ci.totalCounter = cj.getLong("totalCounter");
                    ci.delay = cj.getLong("delay");
                    ci.period = cj.getLong("period");
                    JsonObject cStats = (JsonObject) cj.get("statistics");
                    if (cStats != null) {
                        Object last = cStats.get("lastCreatedExchangeTimestamp");
                        if (last != null) {
                            ci.sinceLastStarted = TimeUtils.printSince(Long.parseLong(last.toString()));
                        }
                        last = cStats.get("lastCompletedExchangeTimestamp");
                        if (last != null) {
                            ci.sinceLastCompleted = TimeUtils.printSince(Long.parseLong(last.toString()));
                        }
                        last = cStats.get("lastFailedExchangeTimestamp");
                        if (last != null) {
                            ci.sinceLastFailed = TimeUtils.printSince(Long.parseLong(last.toString()));
                        }
                    }
                    info.consumers.add(ci);
                }
            }
        }

        // Parse endpoints (top-level "endpoints" is a JsonObject with nested "endpoints" array)
        JsonObject endpointsObj = (JsonObject) root.get("endpoints");
        if (endpointsObj != null) {
            JsonArray endpointList = (JsonArray) endpointsObj.get("endpoints");
            if (endpointList != null) {
                for (Object e : endpointList) {
                    JsonObject ej = (JsonObject) e;
                    EndpointInfo ep = new EndpointInfo();
                    ep.uri = ej.getString("uri");
                    ep.direction = ej.getString("direction");
                    ep.routeId = ej.getString("routeId");
                    ep.hits = TuiHelper.objToLong(ej.get("hits"));
                    ep.stub = Boolean.TRUE.equals(ej.get("stub"));
                    ep.remote = !Boolean.FALSE.equals(ej.get("remote"));
                    ep.minBodySize = TuiHelper.objToLong(ej.get("minBodySize"));
                    ep.maxBodySize = TuiHelper.objToLong(ej.get("maxBodySize"));
                    ep.meanBodySize = TuiHelper.objToLong(ej.get("meanBodySize"));
                    ep.minHeadersSize = TuiHelper.objToLong(ej.get("minHeadersSize"));
                    ep.maxHeadersSize = TuiHelper.objToLong(ej.get("maxHeadersSize"));
                    ep.meanHeadersSize = TuiHelper.objToLong(ej.get("meanHeadersSize"));
                    if (ep.uri != null) {
                        int idx = ep.uri.indexOf(':');
                        ep.component = idx > 0 ? ep.uri.substring(0, idx) : ep.uri;
                    }
                    info.endpoints.add(ep);
                }
            }
        }

        // Parse circuit breakers: resilience4j, fault-tolerance, core
        parseCbSection(root, "resilience4j", info);
        parseCbSection(root, "fault-tolerance", info);
        parseCbSection(root, "circuit-breaker", info);

        // Enrich circuit breakers with processor statistics (matched by id)
        for (CircuitBreakerInfo cb : info.circuitBreakers) {
            if (cb.id != null) {
                for (RouteInfo ri : info.routes) {
                    for (ProcessorInfo pi : ri.processors) {
                        if (cb.id.equals(pi.id)) {
                            cb.total = pi.total;
                            cb.totalFailed = pi.failed;
                            cb.meanTime = pi.meanTime;
                            cb.minTime = pi.minTime;
                            cb.maxTime = pi.maxTime;
                            cb.inflight = pi.inflight;
                            cb.sinceLastStarted = pi.sinceLastStarted;
                            cb.sinceLastSuccess = pi.sinceLastCompleted;
                            cb.sinceLastFail = pi.sinceLastFailed;
                            break;
                        }
                    }
                }
            }
        }

        // Parse error count from error registry
        JsonObject errorsObj = (JsonObject) root.get("errors");
        if (errorsObj != null) {
            info.errorCount = errorsObj.getIntegerOrDefault("size", 0);
        }

        // Parse inflight exchanges
        JsonObject inflightObj = (JsonObject) root.get("inflight");
        if (inflightObj != null) {
            info.inflightBrowseEnabled = inflightObj.getBooleanOrDefault("inflightBrowseEnabled", false);
            int inflightCount = inflightObj.getIntegerOrDefault("inflight", 0);
            if (inflightCount > 0) {
                JsonArray inflArr = (JsonArray) inflightObj.get("exchanges");
                if (inflArr != null) {
                    for (Object ie : inflArr) {
                        JsonObject ij = (JsonObject) ie;
                        InflightInfo ii = new InflightInfo();
                        ii.exchangeId = ij.getString("exchangeId");
                        ii.fromRouteId = ij.getString("fromRouteId");
                        Boolean remote = ij.getBoolean("fromRemoteEndpoint");
                        ii.fromRemoteEndpoint = remote != null && remote;
                        ii.atRouteId = ij.getString("atRouteId");
                        ii.nodeId = ij.getString("nodeId");
                        ii.elapsed = ij.getLongOrDefault("elapsed", 0L);
                        ii.duration = ij.getLongOrDefault("duration", 0L);
                        ii.blocked = false;
                        info.inflightExchanges.add(ii);
                    }
                }
            }
        }

        // Parse blocked exchanges
        JsonObject blockedObj = (JsonObject) root.get("blocked");
        if (blockedObj != null) {
            int blockedCount = blockedObj.getIntegerOrDefault("blocked", 0);
            if (blockedCount > 0) {
                JsonArray blkArr = (JsonArray) blockedObj.get("exchanges");
                if (blkArr != null) {
                    for (Object be : blkArr) {
                        JsonObject bj = (JsonObject) be;
                        InflightInfo ii = new InflightInfo();
                        ii.exchangeId = bj.getString("exchangeId");
                        ii.atRouteId = bj.getString("routeId");
                        ii.nodeId = bj.getString("nodeId");
                        ii.duration = bj.getLongOrDefault("duration", 0L);
                        ii.blocked = true;
                        info.inflightExchanges.add(ii);
                    }
                }
            }
        }

        // Parse micrometer metrics
        JsonObject micrometerObj = (JsonObject) root.get("micrometer");
        if (micrometerObj != null) {
            parseMicrometerMeters(micrometerObj, "counters", "counter", info);
            parseMicrometerMeters(micrometerObj, "gauges", "gauge", info);
            parseMicrometerMeters(micrometerObj, "timers", "timer", info);
            parseMicrometerMeters(micrometerObj, "longTaskTimers", "longTaskTimer", info);
            parseMicrometerMeters(micrometerObj, "distribution", "distribution", info);
        }

        // Parse REST DSL services
        JsonObject restsObj = (JsonObject) root.get("rests");
        if (restsObj != null) {
            JsonArray restList = (JsonArray) restsObj.get("rests");
            if (restList != null) {
                for (Object r : restList) {
                    JsonObject rj = (JsonObject) r;
                    HttpEndpointInfo ep = new HttpEndpointInfo();
                    ep.fromRest = true;
                    ep.url = rj.getString("url");
                    ep.method = rj.getString("method");
                    if (ep.method != null) {
                        ep.method = ep.method.toUpperCase(Locale.ENGLISH);
                    }
                    ep.consumes = rj.getString("consumes");
                    ep.produces = rj.getString("produces");
                    ep.description = rj.getString("description");
                    ep.contractFirst = Boolean.TRUE.equals(rj.get("contractFirst"));
                    ep.specification = Boolean.TRUE.equals(rj.get("specification"));
                    ep.routeId = rj.getString("routeId");
                    ep.operationId = rj.getString("operationId");
                    ep.specificationUri = rj.getString("specificationUri");
                    ep.state = rj.getString("state");
                    ep.inType = rj.getString("inType");
                    ep.outType = rj.getString("outType");
                    Long h = rj.getLong("hits");
                    if (h != null) {
                        ep.hits = h;
                    }
                    ep.path = extractPath(ep.url);
                    info.httpEndpoints.add(ep);
                }
            }
        }

        // Parse Platform-HTTP services
        JsonObject phpObj = (JsonObject) root.get("platform-http");
        if (phpObj != null) {
            info.httpServer = phpObj.getString("server");
            parseHttpEndpoints(phpObj, "endpoints", false, info);
            parseHttpEndpoints(phpObj, "managementEndpoints", true, info);
        }

        fixRestUrlPorts(info);

        // Parse configuration properties
        JsonObject propsObj = (JsonObject) root.get("properties");
        if (propsObj != null) {
            JsonArray propArr = (JsonArray) propsObj.get("properties");
            if (propArr != null) {
                for (Object p : propArr) {
                    JsonObject pj = (JsonObject) p;
                    String key = pj.getString("key");
                    if (key != null && !key.startsWith("camel.jbang.")) {
                        ConfigurationTab.ConfigProperty cp = new ConfigurationTab.ConfigProperty();
                        cp.key = key;
                        cp.value = objToString(pj.get("value"));
                        cp.defaultValue = pj.getString("defaultValue");
                        cp.source = pj.getString("source");
                        cp.location = pj.getString("location");
                        info.configProperties.add(cp);
                    }
                }
                info.configProperties.sort(ConfigurationTab::compareCamelFirst);
            }
        }

        // Parse dataSources
        JsonObject dsObj = (JsonObject) root.get("dataSources");
        if (dsObj != null) {
            JsonArray dsList = (JsonArray) dsObj.get("dataSources");
            if (dsList != null) {
                for (Object d : dsList) {
                    JsonObject dj = (JsonObject) d;
                    DataSourceInfo di = new DataSourceInfo();
                    di.name = dj.getString("name");
                    di.type = dj.getString("type");
                    di.poolType = dj.getString("poolType");
                    di.poolName = dj.getString("poolName");
                    di.active = dj.getIntegerOrDefault("active", 0);
                    di.idle = dj.getIntegerOrDefault("idle", 0);
                    di.total = dj.getIntegerOrDefault("total", 0);
                    di.waiting = dj.getIntegerOrDefault("waiting", 0);
                    di.maxPoolSize = dj.getIntegerOrDefault("maxPoolSize", 0);
                    di.maxUsed = dj.getIntegerOrDefault("maxUsed", 0);
                    di.leakDetection = dj.getIntegerOrDefault("leakDetection", 0);
                    di.created = dj.getIntegerOrDefault("created", 0);
                    info.dataSources.add(di);
                }
            }
        }

        // Parse sqlTrace
        JsonObject sqlTraceObj = (JsonObject) root.get("sqlTrace");
        if (sqlTraceObj != null) {
            JsonObject summary = (JsonObject) sqlTraceObj.get("summary");
            if (summary != null) {
                info.sqlTraceTotal = summary.getLongOrDefault("totalQueries", 0);
                info.sqlTraceAvgTime = summary.getLongOrDefault("avgTime", 0);
                info.sqlTraceSlowestTime = summary.getLongOrDefault("slowestTime", 0);
                info.sqlTraceSlowCount = summary.getLongOrDefault("slowCount", 0);
                info.sqlTraceFailedCount = summary.getLongOrDefault("failedCount", 0);
            }
            JsonArray stmts = (JsonArray) sqlTraceObj.get("statements");
            if (stmts != null) {
                for (Object s : stmts) {
                    JsonObject sj = (JsonObject) s;
                    SqlTraceInfo si = new SqlTraceInfo();
                    si.exchangeId = sj.getString("exchangeId");
                    si.routeId = sj.getString("routeId");
                    si.nodeId = sj.getString("nodeId");
                    si.location = sj.getString("location");
                    si.query = sj.getString("query");
                    si.category = sj.getString("category");
                    si.endpoint = sj.getString("endpoint");
                    si.timestamp = sj.getLongOrDefault("timestamp", 0);
                    si.duration = sj.getLongOrDefault("duration", 0);
                    si.rowCount = sj.getIntegerOrDefault("rowCount", 0);
                    si.updateCount = sj.getIntegerOrDefault("updateCount", 0);
                    si.failed = sj.getBooleanOrDefault("failed", false);
                    info.sqlTraceStatements.add(si);
                }
            }
        }

        return info;
    }

    static TraceEntry parseTraceEntry(JsonObject json, String pid) {
        TraceEntry entry = new TraceEntry();
        entry.pid = pid;
        entry.uid = stringValue(json.get("uid"));
        entry.exchangeId = json.getString("exchangeId");
        entry.routeId = json.getString("routeId");
        entry.nodeId = json.getString("nodeId");
        entry.nodeShortName = json.getString("nodeShortName");
        entry.location = json.getString("location");
        entry.nodeLabel = json.getString("nodeLabel");
        entry.threadName = json.getString("threadName");
        entry.first = json.getBooleanOrDefault("first", false);
        entry.last = json.getBooleanOrDefault("last", false);
        entry.nodeLevel = json.getIntegerOrDefault("nodeLevel", 0);

        boolean done = Boolean.TRUE.equals(json.get("done"));
        boolean failed = Boolean.TRUE.equals(json.get("failed"));
        entry.failed = failed;
        if (entry.failed) {
            entry.status = "Failed";
        } else if (done) {
            entry.status = "Done";
        } else {
            entry.status = "Processing";
        }

        Object elapsedObj = json.get("elapsed");
        if (elapsedObj instanceof Number n) {
            entry.elapsed = n.longValue();
        } else if (elapsedObj != null) {
            try {
                entry.elapsed = Long.parseLong(elapsedObj.toString());
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        Object tsObj = json.get("timestamp");
        if (tsObj instanceof Number n) {
            long epochMs = n.longValue();
            if (entry.last && entry.elapsed > 0) {
                epochMs += entry.elapsed;
            }
            entry.epochMs = epochMs;
            entry.timestamp = Instant.ofEpochMilli(epochMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime().toString();
            if (entry.timestamp.length() > 12) {
                entry.timestamp = entry.timestamp.substring(0, 12);
            }
        } else if (tsObj != null) {
            entry.timestamp = tsObj.toString();
        }

        entry.remoteEndpoint = json.getBooleanOrDefault("remoteEndpoint", false);
        entry.stubEndpoint = json.getBooleanOrDefault("stubEndpoint", false);

        if (entry.first || entry.last) {
            entry.nodeLevel = Math.max(0, entry.nodeLevel - 1);
        }
        String indent = "  ".repeat(entry.nodeLevel);
        if (entry.first) {
            if (entry.stubEndpoint) {
                entry.direction = "~-->";
            } else {
                entry.direction = entry.remoteEndpoint ? "*-->" : "*-> ";
            }
            String uri = json.getString("endpointUri");
            if (uri != null) {
                entry.processor = indent + "from[" + uri + "]";
            } else {
                entry.processor = indent + (entry.nodeLabel != null ? entry.nodeLabel : "");
            }
        } else if (entry.last) {
            if (entry.stubEndpoint) {
                entry.direction = "<--~";
            } else {
                entry.direction = entry.remoteEndpoint ? "<--*" : "<-* ";
            }
            entry.processor = indent + (entry.nodeLabel != null ? entry.nodeLabel : "");
        } else {
            if (entry.stubEndpoint) {
                entry.direction = "~-->";
            } else if (entry.remoteEndpoint) {
                entry.direction = "--->";
            } else {
                entry.direction = "    ";
            }
            entry.processor = indent + (entry.nodeLabel != null ? entry.nodeLabel : "");
        }

        Object msgObj = json.get("message");
        if (msgObj instanceof JsonObject message) {
            MessageData md = parseMessage(message);
            entry.headers = md.headers();
            entry.headerTypes = md.headerTypes();
            entry.body = md.body();
            entry.bodyType = md.bodyType();
            if (entry.body != null) {
                entry.bodyPreview = entry.body.replace("\n", " ").replace("\r", "");
            }
            entry.exchangeProperties = md.exchangeProperties();
            entry.exchangePropertyTypes = md.exchangePropertyTypes();
            entry.exchangeVariables = md.exchangeVariables();
            entry.exchangeVariableTypes = md.exchangeVariableTypes();
        }

        Object excObj = json.get("exception");
        if (excObj instanceof JsonObject excJson) {
            String msg = excJson.getString("message");
            entry.exception = msg != null ? Jsoner.unescape(msg) : null;
            String st = excJson.getString("stackTrace");
            if (st != null && !st.isEmpty()) {
                String ust = Jsoner.unescape(st);
                entry.exception = entry.exception != null ? entry.exception + "\n" + ust : ust;
            }
        }

        return entry;
    }

    static HistoryEntry parseHistoryEntry(JsonObject json, String pid) {
        HistoryEntry entry = new HistoryEntry();
        entry.pid = pid;
        entry.exchangeId = json.getString("exchangeId");
        entry.routeId = json.getString("routeId");
        entry.fromRouteId = json.getString("fromRouteId");
        entry.nodeId = json.getString("nodeId");
        entry.nodeShortName = json.getString("nodeShortName");
        entry.nodeLabel = json.getString("nodeLabel");
        entry.location = json.getString("location");
        entry.threadName = json.getString("threadName");
        entry.first = json.getBooleanOrDefault("first", false);
        entry.last = json.getBooleanOrDefault("last", false);
        entry.failed = json.getBooleanOrDefault("failed", false);
        entry.nodeLevel = json.getIntegerOrDefault("nodeLevel", 0);

        entry.remoteEndpoint = json.getBooleanOrDefault("remoteEndpoint", false);
        entry.stubEndpoint = json.getBooleanOrDefault("stubEndpoint", false);

        Object elapsedObj = json.get("elapsed");
        if (elapsedObj instanceof Number n) {
            entry.elapsed = n.longValue();
        } else {
            entry.elapsed = -1;
        }

        if (entry.first) {
            if (entry.stubEndpoint) {
                entry.direction = "~-->";
            } else {
                entry.direction = entry.remoteEndpoint ? "*-->" : "*-> ";
            }
        } else if (entry.last) {
            if (entry.stubEndpoint) {
                entry.direction = "<--~";
            } else {
                entry.direction = entry.remoteEndpoint ? "<--*" : "<-* ";
            }
        } else {
            if (entry.stubEndpoint) {
                entry.direction = "~-->";
            } else if (entry.remoteEndpoint) {
                entry.direction = "--->";
            } else {
                entry.direction = "    ";
            }
        }

        if (entry.first || entry.last) {
            entry.nodeLevel = Math.max(0, entry.nodeLevel - 1);
        }
        String indent = "  ".repeat(entry.nodeLevel);
        if (entry.first) {
            String uri = json.getString("endpointUri");
            if (uri != null) {
                entry.processor = indent + "from[" + uri + "]";
            } else {
                entry.processor = indent + (entry.nodeLabel != null ? entry.nodeLabel : "");
            }
        } else {
            entry.processor = indent + (entry.nodeLabel != null ? entry.nodeLabel : "");
        }

        Object tsObj = json.get("timestamp");
        if (tsObj instanceof Number n) {
            long epochMs = n.longValue();
            if (entry.last && entry.elapsed > 0) {
                epochMs += entry.elapsed;
            }
            entry.epochMs = epochMs;
            entry.timestamp = Instant.ofEpochMilli(epochMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime().toString();
            if (entry.timestamp.length() > 12) {
                entry.timestamp = entry.timestamp.substring(0, 12);
            }
        }

        Object msgObj = json.get("message");
        if (msgObj instanceof JsonObject message) {
            MessageData md = parseMessage(message);
            entry.headers = md.headers();
            entry.headerTypes = md.headerTypes();
            entry.body = md.body();
            entry.bodyType = md.bodyType();
            entry.exchangeProperties = md.exchangeProperties();
            entry.exchangePropertyTypes = md.exchangePropertyTypes();
            entry.exchangeVariables = md.exchangeVariables();
            entry.exchangeVariableTypes = md.exchangeVariableTypes();
        }

        Object excObj = json.get("exception");
        if (excObj instanceof JsonObject excJson) {
            String msg = excJson.getString("message");
            entry.exception = msg != null ? Jsoner.unescape(msg) : null;
            String st = excJson.getString("stackTrace");
            if (st != null && !st.isEmpty()) {
                String ust = Jsoner.unescape(st);
                entry.exception = entry.exception != null ? entry.exception + "\n" + ust : ust;
            }
        }

        return entry;
    }

    @SuppressWarnings("unchecked")
    static MessageData parseMessage(JsonObject message) {
        Map<String, Object> headers = null;
        Map<String, String> headerTypes = null;
        String body = null;
        String bodyType = null;
        Map<String, Object> exchangeProperties = null;
        Map<String, String> exchangePropertyTypes = null;
        Map<String, Object> exchangeVariables = null;
        Map<String, String> exchangeVariableTypes = null;

        Object headersObj = message.get("headers");
        if (headersObj instanceof List<?> headerList) {
            headers = new LinkedHashMap<>();
            headerTypes = new LinkedHashMap<>();
            for (Object h : headerList) {
                if (h instanceof JsonObject hObj) {
                    String key = String.valueOf(hObj.get("key"));
                    headers.put(key, hObj.get("value"));
                    Object type = hObj.get("type");
                    if (type != null) {
                        headerTypes.put(key, TuiHelper.shortTypeName(type.toString()));
                    }
                }
            }
        } else if (headersObj instanceof Map) {
            headers = new LinkedHashMap<>((Map<String, Object>) headersObj);
        }

        Object bodyObj = message.get("body");
        if (bodyObj instanceof JsonObject bodyJson) {
            Object val = bodyJson.get("value");
            body = val != null ? val.toString() : null;
            bodyType = TuiHelper.shortTypeName(bodyJson.getString("type"));
        } else if (bodyObj != null) {
            body = bodyObj.toString();
        }

        Object propsObj = message.get("exchangeProperties");
        if (propsObj instanceof List<?> propList) {
            exchangeProperties = new LinkedHashMap<>();
            exchangePropertyTypes = new LinkedHashMap<>();
            for (Object p : propList) {
                if (p instanceof JsonObject pObj) {
                    String key = String.valueOf(pObj.get("key"));
                    exchangeProperties.put(key, pObj.get("value"));
                    Object type = pObj.get("type");
                    if (type != null) {
                        exchangePropertyTypes.put(key, TuiHelper.shortTypeName(type.toString()));
                    }
                }
            }
        } else if (propsObj instanceof Map) {
            exchangeProperties = new LinkedHashMap<>((Map<String, Object>) propsObj);
        }

        Object varsObj = message.get("exchangeVariables");
        if (varsObj instanceof List<?> varList) {
            exchangeVariables = new LinkedHashMap<>();
            exchangeVariableTypes = new LinkedHashMap<>();
            for (Object v : varList) {
                if (v instanceof JsonObject vObj) {
                    String key = String.valueOf(vObj.get("key"));
                    exchangeVariables.put(key, vObj.get("value"));
                    Object type = vObj.get("type");
                    if (type != null) {
                        exchangeVariableTypes.put(key, TuiHelper.shortTypeName(type.toString()));
                    }
                }
            }
        } else if (varsObj instanceof Map) {
            exchangeVariables = new LinkedHashMap<>((Map<String, Object>) varsObj);
        }

        return new MessageData(
                headers, headerTypes, body, bodyType,
                exchangeProperties, exchangePropertyTypes, exchangeVariables, exchangeVariableTypes);
    }

    @SuppressWarnings("unchecked")
    static void parseKvArray(JsonArray arr, Map<String, Object> values, Map<String, String> types) {
        if (arr == null) {
            return;
        }
        for (Object o : arr) {
            JsonObject jo = (JsonObject) o;
            String key = jo.getString("key");
            if (key != null) {
                values.put(key, jo.get("value"));
                String type = jo.getString("type");
                if (type != null) {
                    types.put(key, type);
                }
            }
        }
    }

    private static void parseHttpEndpoints(JsonObject phpObj, String key, boolean management, IntegrationInfo info) {
        JsonArray arr = (JsonArray) phpObj.get(key);
        if (arr == null) {
            return;
        }
        for (Object e : arr) {
            JsonObject ej = (JsonObject) e;
            HttpEndpointInfo ep = new HttpEndpointInfo();
            ep.fromRest = false;
            ep.management = management;
            ep.server = phpObj.getString("server");
            ep.url = ej.getString("url");
            ep.path = ej.getString("path");
            ep.method = ej.getString("verbs");
            ep.consumes = ej.getString("consumes");
            ep.produces = ej.getString("produces");
            info.httpEndpoints.add(ep);
        }
    }

    private static void fixRestUrlPorts(IntegrationInfo info) {
        String baseWithPort = null;
        for (HttpEndpointInfo ep : info.httpEndpoints) {
            if (!ep.fromRest && ep.url != null) {
                try {
                    URI uri = URI.create(ep.url);
                    if (uri.getPort() > 0) {
                        baseWithPort = uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
                        break;
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        if (baseWithPort == null) {
            return;
        }
        for (HttpEndpointInfo ep : info.httpEndpoints) {
            if (ep.fromRest && ep.url != null) {
                String path = extractPath(ep.url);
                if (path != null) {
                    int schemeEnd = ep.url.indexOf("://");
                    if (schemeEnd > 0) {
                        String hostPart = ep.url.substring(schemeEnd + 3);
                        int slash = hostPart.indexOf('/');
                        if (slash > 0) {
                            hostPart = hostPart.substring(0, slash);
                        }
                        if (!hostPart.contains(":")) {
                            ep.url = baseWithPort + path;
                        }
                    }
                }
            }
        }
    }

    private static String extractPath(String url) {
        if (url == null) {
            return null;
        }
        int idx = url.indexOf("://");
        if (idx < 0) {
            return url;
        }
        int slash = url.indexOf('/', idx + 3);
        return slash >= 0 ? url.substring(slash) : "/";
    }

    private static void parseCbSection(JsonObject root, String key, IntegrationInfo info) {
        JsonObject section = (JsonObject) root.get(key);
        if (section == null) {
            return;
        }
        JsonArray breakers = (JsonArray) section.get("circuitBreakers");
        if (breakers == null) {
            return;
        }
        String component = switch (key) {
            case "resilience4j" -> "resilience4j";
            case "fault-tolerance" -> "fault-tolerance";
            default -> "core";
        };
        for (Object b : breakers) {
            JsonObject bj = (JsonObject) b;
            CircuitBreakerInfo cb = new CircuitBreakerInfo();
            cb.component = component;
            cb.routeId = bj.getString("routeId");
            cb.id = bj.getString("id");
            cb.state = bj.getString("state");
            cb.bufferedCalls = bj.getIntegerOrDefault("bufferedCalls", 0);
            cb.successfulCalls = TuiHelper.objToLong(bj.get("successfulCalls"));
            cb.failedCalls = TuiHelper.objToLong(bj.get("failedCalls"));
            cb.notPermittedCalls = TuiHelper.objToLong(bj.get("notPermittedCalls"));
            Object fr = bj.get("failureRate");
            cb.failureRate = fr instanceof Number n ? n.doubleValue() : -1;
            info.circuitBreakers.add(cb);
        }
    }

    @SuppressWarnings("unchecked")
    private static void parseMicrometerMeters(
            JsonObject micrometerObj, String section, String type, IntegrationInfo info) {
        JsonArray arr = (JsonArray) micrometerObj.get(section);
        if (arr == null) {
            return;
        }
        for (Object o : arr) {
            JsonObject jo = (JsonObject) o;
            MicrometerMeterInfo m = new MicrometerMeterInfo();
            m.type = type;
            m.name = jo.getString("name");
            m.description = jo.getString("description");
            JsonArray tagsArr = (JsonArray) jo.get("tags");
            if (tagsArr != null) {
                for (Object t : tagsArr) {
                    JsonObject tj = (JsonObject) t;
                    m.tags.add(new String[] { tj.getString("key"), tj.getString("value") });
                }
            }
            switch (type) {
                case "counter":
                    m.count = TuiHelper.objToLong(jo.get("count"));
                    break;
                case "gauge":
                    Object v = jo.get("value");
                    m.value = v instanceof Number n ? n.doubleValue() : null;
                    break;
                case "timer":
                    m.count = TuiHelper.objToLong(jo.get("count"));
                    m.mean = TuiHelper.objToLong(jo.get("mean"));
                    m.max = TuiHelper.objToLong(jo.get("max"));
                    m.total = TuiHelper.objToLong(jo.get("total"));
                    break;
                case "longTaskTimer":
                    Object at = jo.get("activeTasks");
                    m.activeTasks = at instanceof Number n ? n.intValue() : null;
                    m.mean = TuiHelper.objToLong(jo.get("mean"));
                    m.max = TuiHelper.objToLong(jo.get("max"));
                    m.total = TuiHelper.objToLong(jo.get("duration"));
                    break;
                case "distribution":
                    m.count = TuiHelper.objToLong(jo.get("count"));
                    Object dm = jo.get("mean");
                    m.meanDouble = dm instanceof Number n ? n.doubleValue() : null;
                    Object dx = jo.get("max");
                    m.maxDouble = dx instanceof Number n ? n.doubleValue() : null;
                    Object dt = jo.get("totalAmount");
                    m.totalDouble = dt instanceof Number n ? n.doubleValue() : null;
                    break;
                default:
                    break;
            }
            info.meters.add(m);
        }
    }

    static long extractSince(ProcessHandle ph) {
        return ph.info().startInstant().map(Instant::toEpochMilli).orElse(0L);
    }

    @SuppressWarnings("unchecked")
    static List<ErrorInfo> parseErrors(JsonObject root) {
        JsonArray errorList = (JsonArray) root.get("errors");
        if (errorList == null) {
            return List.of();
        }
        List<ErrorInfo> parsed = new ArrayList<>();
        for (Object e : errorList) {
            JsonObject ej = (JsonObject) e;
            ErrorInfo ei = new ErrorInfo();
            ei.routeId = ej.getString("routeId");
            ei.nodeId = ej.getString("nodeId");
            ei.exchangeId = ej.getString("exchangeId");
            ei.handled = Boolean.TRUE.equals(ej.get("handled"));
            Long ts = ej.getLong("timestamp");
            if (ts != null) {
                ei.timestamp = ts;
            }
            ei.location = ej.getString("location");
            ei.threadName = ej.getString("threadName");
            Long elapsed = ej.getLong("elapsed");
            if (elapsed != null) {
                ei.elapsed = elapsed;
            }
            ei.endpointUri = ej.getString("endpointUri");
            ei.fromEndpointUri = ej.getString("fromEndpointUri");
            JsonObject ex = (JsonObject) ej.get("exception");
            if (ex != null) {
                ei.exceptionType = ex.getString("type");
                ei.exceptionMessage = ex.getString("message");
                ei.stackTrace = ex.getString("stackTrace");
            }
            Object mhObj = ej.get("messageHistory");
            if (mhObj instanceof JsonArray mhArr) {
                ei.messageHistory = new String[mhArr.size()];
                for (int i = 0; i < mhArr.size(); i++) {
                    ei.messageHistory[i] = mhArr.get(i).toString();
                }
            }
            JsonObject msg = (JsonObject) ej.get("message");
            if (msg != null) {
                Object bodyObj = msg.get("body");
                if (bodyObj instanceof JsonObject bodyJson) {
                    ei.body = bodyJson.getString("value");
                    ei.bodyType = bodyJson.getString("type");
                } else if (bodyObj != null) {
                    ei.body = bodyObj.toString();
                }
                JsonArray hdrs = msg.getCollection("headers");
                if (hdrs != null) {
                    parseKvArray(hdrs, ei.headers, ei.headerTypes);
                }
            }
            JsonArray props = ej.getCollection("exchangeProperties");
            if (props != null) {
                parseKvArray(props, ei.properties, ei.propertyTypes);
            }
            JsonArray vars = ej.getCollection("exchangeVariables");
            if (vars != null) {
                parseKvArray(vars, ei.variables, ei.variableTypes);
            }
            parsed.add(ei);
        }
        return parsed;
    }

    static List<ActivityEntry> parseActivityEntries(JsonObject root) {
        JsonArray activityList = (JsonArray) root.get("activity");
        if (activityList == null) {
            return List.of();
        }
        List<ActivityEntry> parsed = new ArrayList<>();
        for (Object e : activityList) {
            JsonObject aj = (JsonObject) e;
            ActivityEntry ae = new ActivityEntry();
            Object uidObj = aj.get("uid");
            if (uidObj instanceof Number n) {
                ae.uid = n.longValue();
            }
            ae.exchangeId = aj.getString("exchangeId");
            ae.routeId = aj.getString("routeId");
            ae.fromRouteId = aj.getString("fromRouteId");
            Long ts = aj.getLong("timestamp");
            if (ts != null) {
                ae.timestamp = ts;
            }
            Object elapsedObj = aj.get("elapsed");
            if (elapsedObj instanceof Number n) {
                ae.elapsed = n.longValue();
            }
            ae.failed = aj.getBooleanOrDefault("failed", false);
            ae.fromEndpointUri = aj.getString("fromEndpointUri");
            JsonArray sends = aj.getCollection("endpointSends");
            if (sends != null) {
                for (Object s : sends) {
                    JsonObject so = (JsonObject) s;
                    ActivityEntry.EndpointSendEntry se = new ActivityEntry.EndpointSendEntry();
                    se.endpointUri = so.getString("endpointUri");
                    se.remoteEndpoint = so.getBooleanOrDefault("remoteEndpoint", false);
                    Object sendElapsed = so.get("elapsed");
                    if (sendElapsed instanceof Number sn) {
                        se.elapsed = sn.longValue();
                    }
                    ae.endpointSends.add(se);
                }
            }
            ae.exceptionMessage = aj.getString("exception");
            parsed.add(ae);
        }
        return parsed;
    }

    static String stringValue(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    static String objToString(Object o) {
        return o != null ? o.toString() : "";
    }

    static long objToLong(Object o) {
        return TuiHelper.objToLong(o);
    }
}
