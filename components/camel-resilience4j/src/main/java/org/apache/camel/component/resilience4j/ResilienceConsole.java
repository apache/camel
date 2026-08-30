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
package org.apache.camel.component.resilience4j;

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

@DevConsole(name = "resilience4j", displayName = "Resilience Circuit Breaker",
            description = "Display circuit breaker information")
public class ResilienceConsole extends AbstractDevConsole {

    public record Configuration(
            @Metadata(description = "The failure rate threshold in percentage") float failureRateThreshold,
            @Metadata(description = "The slow call rate threshold in percentage") float slowCallRateThreshold,
            @Metadata(description = "The minimum number of calls before the failure rate is calculated") int minimumNumberOfCalls,
            @Metadata(description = "The number of permitted calls when the circuit breaker is half open") int permittedNumberOfCallsInHalfOpenState,
            @Metadata(description = "The sliding window size") int slidingWindowSize,
            @Metadata(description = "The sliding window type") String slidingWindowType,
            @Metadata(description = "The wait duration in the open state, in milliseconds") long waitDurationInOpenState,
            @Metadata(description = "Whether the circuit breaker automatically transitions from open to half-open") boolean automaticTransitionFromOpenToHalfOpen,
            @Metadata(description = "Whether the bulkhead is enabled") boolean bulkheadEnabled,
            @Metadata(description = "The maximum concurrent calls allowed by the bulkhead (only present when bulkheadEnabled is true)") Integer bulkheadMaxConcurrentCalls,
            @Metadata(description = "The maximum wait duration for the bulkhead in milliseconds (only present when bulkheadEnabled is true)") Long bulkheadMaxWaitDuration,
            @Metadata(description = "Whether the timeout is enabled") boolean timeoutEnabled,
            @Metadata(description = "The timeout duration in milliseconds (only present when timeoutEnabled is true)") Long timeoutDuration) {
    }

    public record CircuitBreakerEntry(
            @Metadata(description = "The circuit breaker id") String id,
            @Metadata(description = "The route id") String routeId,
            @Metadata(description = "The circuit breaker state") String state,
            @Metadata(description = "The number of buffered calls") int bufferedCalls,
            @Metadata(description = "The number of successful calls") int successfulCalls,
            @Metadata(description = "The number of failed calls") int failedCalls,
            @Metadata(description = "The number of not permitted calls") long notPermittedCalls,
            @Metadata(description = "The failure rate in percentage") float failureRate,
            @Metadata(description = "The circuit breaker configuration") Configuration configuration) {
    }

    public record Response(@Metadata(description = "The circuit breakers") List<CircuitBreakerEntry> circuitBreakers) {
    }

    public ResilienceConsole() {
        super("camel", "resilience4j", "Resilience Circuit Breaker", "Display circuit breaker information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        List<ResilienceProcessor> cbs = new ArrayList<>();
        for (Route route : getCamelContext().getRoutes()) {
            List<Processor> list = route.filter("*");
            for (Processor p : list) {
                if (p instanceof ResilienceProcessor) {
                    cbs.add((ResilienceProcessor) p);
                }
            }
        }
        // sort by ids
        cbs.sort(Comparator.comparing(ResilienceProcessor::getId));

        for (ResilienceProcessor cb : cbs) {
            String id = cb.getId();
            String rid = cb.getRouteId();
            String state = cb.getCircuitBreakerState();
            int sc = cb.getNumberOfSuccessfulCalls();
            int bc = cb.getNumberOfBufferedCalls();
            int fc = cb.getNumberOfFailedCalls();
            long npc = cb.getNumberOfNotPermittedCalls();
            float fr = cb.getFailureRate();
            if (fr >= 0) {
                sb.append(String.format("    %s/%s: %s (buffered: %d success: %d failure: %d/%.0f%% not-permitted: %d)%n", rid,
                        id, state, bc, sc, fc, fr, npc));
            } else {
                sb.append(String.format("    %s/%s: %s (buffered: %d success: %d failure: %d not-permitted: %d)%n", rid, id,
                        state, bc, sc, fc, npc));
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<ResilienceProcessor> cbs = new ArrayList<>();
        for (Route route : getCamelContext().getRoutes()) {
            List<Processor> list = route.filter("*");
            for (Processor p : list) {
                if (p instanceof ResilienceProcessor) {
                    cbs.add((ResilienceProcessor) p);
                }
            }
        }
        // sort by ids
        cbs.sort(Comparator.comparing(ResilienceProcessor::getId));

        final List<CircuitBreakerEntry> list = new ArrayList<>();
        for (ResilienceProcessor cb : cbs) {
            Configuration config = new Configuration(
                    cb.getCircuitBreakerFailureRateThreshold(), cb.getCircuitBreakerSlowCallRateThreshold(),
                    cb.getCircuitBreakerMinimumNumberOfCalls(), cb.getCircuitBreakerPermittedNumberOfCallsInHalfOpenState(),
                    cb.getCircuitBreakerSlidingWindowSize(), cb.getCircuitBreakerSlidingWindowType(),
                    cb.getCircuitBreakerWaitDurationInOpenState(),
                    cb.isCircuitBreakerTransitionFromOpenToHalfOpenEnabled(), cb.isBulkheadEnabled(),
                    cb.isBulkheadEnabled() ? cb.getBulkheadMaxConcurrentCalls() : null,
                    cb.isBulkheadEnabled() ? cb.getBulkheadMaxWaitDuration() : null, cb.isTimeoutEnabled(),
                    cb.isTimeoutEnabled() ? cb.getTimeoutDuration() : null);

            list.add(new CircuitBreakerEntry(
                    cb.getId(), cb.getRouteId(), cb.getCircuitBreakerState(), cb.getNumberOfBufferedCalls(),
                    cb.getNumberOfSuccessfulCalls(), cb.getNumberOfFailedCalls(), cb.getNumberOfNotPermittedCalls(),
                    cb.getFailureRate(), config));
        }

        Response response = new Response(list);
        return JsonRecordSupport.toJsonObject(response);
    }
}
