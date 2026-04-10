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
package org.apache.camel.telemetrydev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.telemetry.Op;
import org.apache.camel.telemetry.TagConstants;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.awaitility.Awaitility.await;

public class TelemetryDevTracerTestSupport extends ExchangeTestSupport {

    private final ObjectMapper mapper = new ObjectMapper();

    /*
     * Clear the log traces before each test to prevent flaky tests from leftover data
     */
    @BeforeEach
    public synchronized void clearLogTracesBeforeTest() throws IOException {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        RollingFileAppender appender = (RollingFileAppender) ctx.getConfiguration().getAppenders().get("file2");
        if (appender != null) {
            appender.getManager().rollover();
        }
    }

    protected Map<String, DevTrace> tracesFromLog() throws IOException {
        Map<String, DevTrace> answer = new HashMap<>();
        Path path = Paths.get("target/telemetry-traces.log");
        if (!Files.exists(path)) {
            return answer;
        }
        List<String> allTraces = Files.readAllLines(path);
        for (String trace : allTraces) {
            DevTrace st = mapper.readValue(trace, DevTrace.class);
            if (answer.get(st.getTraceId()) != null) {
                // Multiple traces exists for this traceId: this may happen
                // when we deal with async events (like wiretap and the like)
                DevTrace existing = answer.get(st.getTraceId());
                List<DevSpanAdapter> mergedSpans = st.getSpans();
                mergedSpans.addAll(existing.getSpans());
                st = new DevTrace(st.getTraceId(), mergedSpans);
            }
            answer.put(st.getTraceId(), st);
        }

        return answer;
    }

    /**
     * Wait for the expected number of traces to be written to the log file. Uses Awaitility to avoid flaky tests from
     * timing issues.
     */
    protected Map<String, DevTrace> awaitTracesFromLog(int expectedCount) {
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    try {
                        Map<String, DevTrace> traces = tracesFromLog();
                        return traces.size() >= expectedCount;
                    } catch (IOException e) {
                        return false;
                    }
                });
        try {
            return tracesFromLog();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read traces from log", e);
        }
    }

    /*
     * This one is required to rollover the log traces database file and make sure each test has its own
     * set of fresh data.
     */
    @AfterEach
    public synchronized void clearLogTraces() throws IOException {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        RollingFileAppender appender = (RollingFileAppender) ctx.getConfiguration().getAppenders().get("file2");
        if (appender != null) {
            appender.getManager().rollover();
        }
    }

    protected static DevSpanAdapter getSpan(List<DevSpanAdapter> trace, String uri, Op op) {
        for (DevSpanAdapter span : trace) {
            if (span.getTag("camel.uri") != null && span.getTag("camel.uri").equals(uri)) {
                if (span.getTag(TagConstants.OP).equals(op.toString())) {
                    return span;
                }
            }
        }
        throw new IllegalArgumentException("Trying to get a non existing span!");
    }

}
