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
package org.apache.camel.impl.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.TypeConverter;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.TypeConvertible;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "type-converters", description = "Camel Type Converter information")
public class TypeConverterConsole extends AbstractDevConsole {

    public record Statistics(
            @Metadata(description = "Number of attempted conversions") long attemptCounter,
            @Metadata(description = "Number of successful conversions (cache hit)") long hitCounter,
            @Metadata(description = "Number of cache misses") long missCounter,
            @Metadata(description = "Number of failed conversions") long failedCounter,
            @Metadata(description = "Number of noop conversions") long noopCounter) {
    }

    public record Converter(
            @Metadata(description = "The source type") String from,
            @Metadata(description = "The target type") String to,
            @Metadata(description = "The type converter implementation class") String converterClass) {
    }

    public record Response(
            @Metadata(description = "Number of registered type converters") int size,
            @Metadata(description = "Whether a type converter must exist or not") String exists,
            @Metadata(description = "Logging level used when a type converter does not exist") String existsLoggingLevel,
            @Metadata(description = "Type converter usage statistics (only present when statistics are enabled)") Statistics statistics,
            @Metadata(description = "The registered type converters") List<Converter> converters) {
    }

    public TypeConverterConsole() {
        super("camel", "type-converters", "Type Converters", "Camel Type Converter information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        TypeConverterRegistry reg = getCamelContext().getTypeConverterRegistry();
        sb.append(String.format("%n    Converters: %s", reg.size()));
        sb.append(String.format("%n    Exists: %s", reg.getTypeConverterExists().name()));
        sb.append(String.format("%n    Exists LoggingLevel: %s", reg.getTypeConverterExistsLoggingLevel()));
        final TypeConverterRegistry.Statistics statistics = reg.getStatistics();

        statistics.computeIfEnabled(statistics::getAttemptCounter, v -> sb.append(String.format("%n    Attempts: %s", v)));
        statistics.computeIfEnabled(statistics::getHitCounter, v -> sb.append(String.format("%n    Hit: %s", v)));
        statistics.computeIfEnabled(statistics::getMissCounter, v -> sb.append(String.format("%n    Miss: %s", v)));
        statistics.computeIfEnabled(statistics::getFailedCounter, v -> sb.append(String.format("%n    Failed: %s", v)));
        statistics.computeIfEnabled(statistics::getNoopCounter, v -> sb.append(String.format("%n    Noop: %s", v)));

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        TypeConverterRegistry reg = getCamelContext().getTypeConverterRegistry();

        final TypeConverterRegistry.Statistics statistics = reg.getStatistics();
        AtomicLong attemptCounter = new AtomicLong();
        AtomicLong hitCounter = new AtomicLong();
        AtomicLong missCounter = new AtomicLong();
        AtomicLong failedCounter = new AtomicLong();
        AtomicLong noopCounter = new AtomicLong();
        AtomicBoolean statisticsEnabled = new AtomicBoolean();

        statistics.computeIfEnabled(statistics::getAttemptCounter, v -> {
            attemptCounter.set(v);
            statisticsEnabled.set(true);
        });
        statistics.computeIfEnabled(statistics::getHitCounter, v -> {
            hitCounter.set(v);
            statisticsEnabled.set(true);
        });
        statistics.computeIfEnabled(statistics::getMissCounter, v -> {
            missCounter.set(v);
            statisticsEnabled.set(true);
        });
        statistics.computeIfEnabled(statistics::getFailedCounter, v -> {
            failedCounter.set(v);
            statisticsEnabled.set(true);
        });
        statistics.computeIfEnabled(statistics::getNoopCounter, v -> {
            noopCounter.set(v);
            statisticsEnabled.set(true);
        });

        Statistics stats = statisticsEnabled.get()
                ? new Statistics(
                        attemptCounter.get(), hitCounter.get(), missCounter.get(), failedCounter.get(), noopCounter.get())
                : null;

        List<Converter> converters = new ArrayList<>();
        for (Map.Entry<TypeConvertible<?, ?>, TypeConverter> e : reg.listTypeConverters().entrySet()) {
            TypeConvertible<?, ?> tc = e.getKey();
            converters.add(new Converter(
                    tc.getFrom().getCanonicalName(), tc.getTo().getCanonicalName(), e.getValue().getClass().getName()));
        }

        Response response = new Response(
                reg.size(), reg.getTypeConverterExists().name(), reg.getTypeConverterExistsLoggingLevel().name(), stats,
                converters);
        return JsonRecordSupport.toJsonObject(response);
    }
}
