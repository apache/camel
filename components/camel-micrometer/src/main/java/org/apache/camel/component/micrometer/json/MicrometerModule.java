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
package org.apache.camel.component.micrometer.json;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.search.Search;

public class MicrometerModule extends Module {

    static final Version VERSION = new Version(1, 0, 5, "", "io.micrometer", "micrometer-core");

    private final TimeUnit timeUnit;
    private final Iterable<Tag> matchingTags;
    private final Predicate<String> matchingNames;

    public MicrometerModule(TimeUnit timeUnit) {
        this(timeUnit, name -> true, Tags.empty());
    }

    public MicrometerModule(TimeUnit timeUnit, Predicate<String> matchingNames, Iterable<Tag> matchingTags) {
        this.timeUnit = timeUnit;
        this.matchingNames = matchingNames;
        this.matchingTags = matchingTags;
    }

    @Override
    public String getModuleName() {
        return "micrometer";
    }

    @Override
    public Version version() {
        return VERSION;
    }


    private static final class IdSerializer extends StdSerializer<Meter.Id> {
        private IdSerializer() {
            super(Meter.Id.class);
        }

        @Override
        public void serialize(Meter.Id id, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeStartObject();
            json.writeStringField("name", id.getName());
            json.writeObjectField("tags", id.getTags());
            json.writeEndObject();
        }
    }

    private static final class TagSerializer extends StdSerializer<Tag> {
        private TagSerializer() {
            super(Tag.class);
        }

        @Override
        public void serialize(Tag tag, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeStartObject();
            json.writeStringField(tag.getKey(), tag.getValue());
            json.writeEndObject();
        }
    }

    private abstract static class MeterSerializer<T extends Meter> extends StdSerializer<T> {

        private MeterSerializer(Class<T> clazz) {
            super(clazz);
        }

        @Override
        public final void serialize(T meter, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeStartObject();
            json.writeObjectField("id", meter.getId());
            serializeStatistics(meter, json, provider);
            json.writeEndObject();
        }

        protected abstract void serializeStatistics(T meter, JsonGenerator json, SerializerProvider provider) throws IOException;

        protected static void serializeSnapshot(JsonGenerator json, HistogramSnapshot snapshot, TimeUnit timeUnit) throws IOException {
            json.writeNumberField("count", snapshot.count());
            json.writeNumberField("max", snapshot.max(timeUnit));
            json.writeNumberField("mean", snapshot.mean(timeUnit));
            json.writeNumberField("total", snapshot.total(timeUnit));
            ValueAtPercentile[] percentiles = snapshot.percentileValues();
            for (ValueAtPercentile percentile : percentiles) {
                json.writeNumberField(String.format("p%0.3d", percentile.percentile()), percentile.value(timeUnit));
            }
        }
    }

    private static final class GaugeSerializer extends MeterSerializer<Gauge> {
        private GaugeSerializer() {
            super(Gauge.class);
        }

        @Override
        protected void serializeStatistics(Gauge gauge, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeNumberField("value", gauge.value());
        }
    }

    private static final class CounterSerializer extends MeterSerializer<Counter> {
        private CounterSerializer() {
            super(Counter.class);
        }

        @Override
        protected void serializeStatistics(Counter counter, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeNumberField("count", counter.count());
        }
    }

    private static final class FunctionCounterSerializer extends MeterSerializer<FunctionCounter> {
        private FunctionCounterSerializer() {
            super(FunctionCounter.class);
        }

        @Override
        protected void serializeStatistics(FunctionCounter counter, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeNumberField("count", counter.count());
        }
    }

    private static final class TimerSerializer extends MeterSerializer<AbstractTimer> {

        private final TimeUnit timeUnit;

        private TimerSerializer(TimeUnit timeUnit) {
            super(AbstractTimer.class);
            this.timeUnit = timeUnit;
        }

        @Override
        protected void serializeStatistics(AbstractTimer timer, JsonGenerator json, SerializerProvider provider) throws IOException {
            serializeSnapshot(json, timer.takeSnapshot(), timeUnit);
        }

    }

    private static final class FunctionTimerSerializer extends MeterSerializer<FunctionTimer> {

        private final TimeUnit timeUnit;

        private FunctionTimerSerializer(TimeUnit timeUnit) {
            super(FunctionTimer.class);
            this.timeUnit = timeUnit;
        }

        @Override
        protected void serializeStatistics(FunctionTimer timer, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeNumberField("count", timer.count());
            json.writeNumberField("mean", timer.mean(timeUnit));
            json.writeNumberField("total", timer.totalTime(timeUnit));
        }
    }

    private static final class LongTaskTimerSerializer extends MeterSerializer<LongTaskTimer> {

        private final TimeUnit timeUnit;

        private LongTaskTimerSerializer(TimeUnit timeUnit) {
            super(LongTaskTimer.class);
            this.timeUnit = timeUnit;
        }

        @Override
        protected void serializeStatistics(LongTaskTimer timer, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeNumberField("activeTasks", timer.activeTasks());
            json.writeNumberField("duration", timer.duration(timeUnit));
        }
    }

    private static final class DistributionSummarySerializer extends MeterSerializer<AbstractDistributionSummary> {

        private final TimeUnit timeUnit;

        private DistributionSummarySerializer(TimeUnit timeUnit) {
            super(AbstractDistributionSummary.class);
            this.timeUnit = timeUnit;
        }

        @Override
        protected void serializeStatistics(AbstractDistributionSummary distributionSummary,
                                           JsonGenerator json,
                                           SerializerProvider provider) throws IOException {
            serializeSnapshot(json, distributionSummary.takeSnapshot(), timeUnit);
        }
    }


    private static final class MeterRegistrySerializer extends StdSerializer<MeterRegistry> {

        private final Predicate<String> matchingNames;
        private final Iterable<Tag> matchingTags;

        private MeterRegistrySerializer(Predicate<String> matchingNames, Iterable<Tag> matchingTags) {
            super(MeterRegistry.class);
            this.matchingNames = matchingNames;
            this.matchingTags = matchingTags;
        }


        @Override
        public void serialize(MeterRegistry registry,
                              JsonGenerator json,
                              SerializerProvider provider) throws IOException {

            json.writeStartObject();
            json.writeStringField("version", VERSION.toString());
            json.writeObjectField("gauges", meters(registry, Gauge.class, matchingNames, matchingTags));
            json.writeObjectField("counters", meters(registry, Counter.class, matchingNames, matchingTags));
            json.writeObjectField("functionCounters", meters(registry, FunctionCounter.class, matchingNames, matchingTags));
            json.writeObjectField("timers", meters(registry, Timer.class, matchingNames, matchingTags));
            json.writeObjectField("functionTimers", meters(registry, FunctionTimer.class, matchingNames, matchingTags));
            json.writeObjectField("longTaskTimers", meters(registry, LongTaskTimer.class, matchingNames, matchingTags));
            json.writeObjectField("distributionSummaries", meters(registry, DistributionSummary.class, matchingNames, matchingTags));
            json.writeEndObject();
        }

        private Set<Meter> meters(MeterRegistry meterRegistry, Class<? extends Meter> clazz, Predicate<String> matchingNames, Iterable<Tag> matchingTags) {
            if (meterRegistry instanceof CompositeMeterRegistry) {
                return ((CompositeMeterRegistry) meterRegistry).getRegistries().stream()
                        .flatMap(reg -> meters(reg, clazz, matchingNames, matchingTags).stream())
                        .sorted(Comparator.comparing(o -> o.getId().getName()))
                        .collect(Collectors.toSet());
            }
            return Search.in(meterRegistry).name(matchingNames).tags(matchingTags).meters().stream()
                    .filter(clazz::isInstance)
                    .sorted(Comparator.comparing(o -> o.getId().getName()))
                    .collect(Collectors.toSet());
        }

    }


    @Override
    public void setupModule(SetupContext setupContext) {
        setupContext.addSerializers(new SimpleSerializers(Arrays.asList(
                new IdSerializer(),
                new TagSerializer(),
                new GaugeSerializer(),
                new CounterSerializer(),
                new FunctionCounterSerializer(),
                new TimerSerializer(timeUnit),
                new FunctionTimerSerializer(timeUnit),
                new LongTaskTimerSerializer(timeUnit),
                new DistributionSummarySerializer(timeUnit),
                new MeterRegistrySerializer(matchingNames, matchingTags)
        )));
    }
}
