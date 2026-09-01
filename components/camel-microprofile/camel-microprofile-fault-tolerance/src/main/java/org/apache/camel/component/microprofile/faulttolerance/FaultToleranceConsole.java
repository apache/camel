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
package org.apache.camel.component.microprofile.faulttolerance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "fault-tolerance", displayName = "MicroProfile Circuit Breaker",
            description = "Display circuit breaker information")
public class FaultToleranceConsole extends AbstractDevConsole {

    public record Configuration(
            @Metadata(description = "The delay in milliseconds") long delay,
            @Metadata(description = "The failure ratio") float failureRatio,
            @Metadata(description = "The request volume threshold") int requestVolumeThreshold,
            @Metadata(description = "The success threshold") int successThreshold,
            @Metadata(description = "Whether the bulkhead is enabled") boolean bulkheadEnabled,
            @Metadata(description = "The bulkhead maximum concurrent calls (only present when the bulkhead is enabled)") Integer bulkheadMaxConcurrentCalls,
            @Metadata(description = "The bulkhead waiting task queue size (only present when the bulkhead is enabled)") Integer bulkheadWaitingTaskQueue,
            @Metadata(description = "Whether the timeout is enabled") boolean timeoutEnabled,
            @Metadata(description = "The timeout duration in milliseconds (only present when the timeout is enabled)") Long timeoutDuration) {
    }

    public record CircuitBreakerEntry(
            @Metadata(description = "The circuit breaker ID") String id,
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "The circuit breaker state") String state,
            @Metadata(description = "Number of successful calls") long successfulCalls,
            @Metadata(description = "Number of failed calls") long failedCalls,
            @Metadata(description = "Number of not-permitted calls") long notPermittedCalls,
            @Metadata(description = "The circuit breaker configuration") Configuration configuration) {
    }

    public record Response(@Metadata(description = "The circuit breakers") List<CircuitBreakerEntry> circuitBreakers) {
    }

    public FaultToleranceConsole() {
        super("camel", "fault-tolerance", "MicroProfile Circuit Breaker",
              "Display circuit breaker information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        List<FaultToleranceProcessor> cbs = new ArrayList<>();
        for (Route route : getCamelContext().getRoutes()) {
            List<Processor> list = route.filter("*");
            for (Processor p : list) {
                if (p instanceof FaultToleranceProcessor) {
                    cbs.add((FaultToleranceProcessor) p);
                }
            }
        }
        // sort by ids
        cbs.sort(Comparator.comparing(FaultToleranceProcessor::getId));

        for (FaultToleranceProcessor cb : cbs) {
            String id = cb.getId();
            String rid = cb.getRouteId();
            String state = cb.getCircuitBreakerState();
            long sc = cb.getNumberOfSuccessfulCalls();
            long fc = cb.getNumberOfFailedCalls();
            long npc = cb.getNumberOfNotPermittedCalls();
            sb.append(String.format("    %s/%s: %s (success: %d failure: %d not-permitted: %d)%n",
                    rid, id, state, sc, fc, npc));
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<FaultToleranceProcessor> cbs = new ArrayList<>();
        for (Route route : getCamelContext().getRoutes()) {
            List<Processor> list = route.filter("*");
            for (Processor p : list) {
                if (p instanceof FaultToleranceProcessor) {
                    cbs.add((FaultToleranceProcessor) p);
                }
            }
        }
        // sort by ids
        cbs.sort(Comparator.comparing(FaultToleranceProcessor::getId));

        final List<CircuitBreakerEntry> list = new ArrayList<>();
        for (FaultToleranceProcessor cb : cbs) {
            Integer bulkheadMaxConcurrentCalls = null;
            Integer bulkheadWaitingTaskQueue = null;
            if (cb.isBulkheadEnabled()) {
                bulkheadMaxConcurrentCalls = cb.getBulkheadMaxConcurrentCalls();
                bulkheadWaitingTaskQueue = cb.getBulkheadWaitingTaskQueue();
            }
            Long timeoutDuration = cb.isTimeoutEnabled() ? cb.getTimeoutDuration() : null;

            Configuration config = new Configuration(
                    cb.getDelay(), cb.getFailureRatio(), cb.getRequestVolumeThreshold(), cb.getSuccessThreshold(),
                    cb.isBulkheadEnabled(), bulkheadMaxConcurrentCalls, bulkheadWaitingTaskQueue, cb.isTimeoutEnabled(),
                    timeoutDuration);

            list.add(new CircuitBreakerEntry(
                    cb.getId(), cb.getRouteId(), cb.getCircuitBreakerState(), cb.getNumberOfSuccessfulCalls(),
                    cb.getNumberOfFailedCalls(), cb.getNumberOfNotPermittedCalls(), config));
        }

        Response response = new Response(list);
        return JsonRecordSupport.toJsonObject(response);
    }
}
