/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.micrometer.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.search.Search;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Christian Ohr
 */
public class MicrometerModule extends Module {

    static final Version VERSION = new Version(1, 0, 2, "", "io.micrometer", "micrometer-core");

    private final TimeUnit timeUnit;
    private final boolean supportAggregablePercentiles;

    public MicrometerModule(TimeUnit timeUnit, boolean supportAggregablePercentiles) {
        this.timeUnit = timeUnit;
        this.supportAggregablePercentiles = supportAggregablePercentiles;
    }

    @Override
    public String getModuleName() {
        return "micrometer";
    }

    @Override
    public Version version() {
        return VERSION;
    }


    private static class IdSerializer extends StdSerializer<Meter.Id> {
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

    private static class TagSerializer extends StdSerializer<Tag> {
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
    }

    private static class GaugeSerializer extends MeterSerializer<Gauge> {
        private GaugeSerializer() {
            super(Gauge.class);
        }

        @Override
        protected void serializeStatistics(Gauge gauge, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeNumberField("value", gauge.value());
        }
    }

    private static class CounterSerializer extends MeterSerializer<Counter> {
        private CounterSerializer() {
            super(Counter.class);
        }

        @Override
        protected void serializeStatistics(Counter counter, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeNumberField("count", counter.count());
        }
    }

    private static class FunctionCounterSerializer extends MeterSerializer<FunctionCounter> {
        private FunctionCounterSerializer() {
            super(FunctionCounter.class);
        }

        @Override
        protected void serializeStatistics(FunctionCounter counter, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeNumberField("count", counter.count());
        }
    }

    private static class TimerSerializer extends MeterSerializer<AbstractTimer> {

        private final TimeUnit timeUnit;
        private final boolean supportAggregablePercentiles;

        private TimerSerializer(TimeUnit timeUnit, boolean supportAggregablePercentiles) {
            super(AbstractTimer.class);
            this.timeUnit = timeUnit;
            this.supportAggregablePercentiles = supportAggregablePercentiles;
        }

        @Override
        protected void serializeStatistics(AbstractTimer timer, JsonGenerator json, SerializerProvider provider) throws IOException {
            HistogramSnapshot snapshot = timer.takeSnapshot(supportAggregablePercentiles);
            json.writeNumberField("count", snapshot.count());
            json.writeNumberField("max", snapshot.max(timeUnit));
            json.writeNumberField("mean", snapshot.mean(timeUnit));
            json.writeNumberField("total", snapshot.total(timeUnit));
            double[] percentiles = timer.statsConfig().getPercentiles();
            for (int idx = 0; idx < percentiles.length; idx++) {
                json.writeNumberField(String.format("p%0.3d", percentiles[idx]), snapshot.percentileValues()[idx].value(timeUnit));
            }
        }
    }

    private static class FunctionTimerSerializer extends MeterSerializer<FunctionTimer> {

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

    private static class LongTaskTimerSerializer extends MeterSerializer<LongTaskTimer> {

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

    private static class DistributionSummarySerializer extends MeterSerializer<AbstractDistributionSummary> {

        private final TimeUnit timeUnit;
        private final boolean supportAggregablePercentiles;

        public DistributionSummarySerializer(TimeUnit timeUnit, boolean supportAggregablePercentiles) {
            super(AbstractDistributionSummary.class);
            this.timeUnit = timeUnit;
            this.supportAggregablePercentiles = supportAggregablePercentiles;
        }

        @Override
        protected void serializeStatistics(AbstractDistributionSummary distributionSummary,
                                           JsonGenerator json,
                                           SerializerProvider provider) throws IOException {
            HistogramSnapshot snapshot = distributionSummary.takeSnapshot(supportAggregablePercentiles);
            json.writeNumberField("count", snapshot.count());
            json.writeNumberField("max", snapshot.max(timeUnit));
            json.writeNumberField("mean", snapshot.mean(timeUnit));
            json.writeNumberField("total", snapshot.total(timeUnit));
            double[] percentiles = distributionSummary.statsConfig().getPercentiles();
            for (int idx = 0; idx < percentiles.length; idx++) {
                json.writeNumberField(String.format("p%0.3d", percentiles[idx]), snapshot.percentileValues()[idx].value(timeUnit));
            }
        }
    }


    private static class MeterRegistrySerializer extends StdSerializer<MeterRegistry> {

        private MeterRegistrySerializer() {
            super(MeterRegistry.class);
        }


        @Override
        public void serialize(MeterRegistry registry,
                              JsonGenerator json,
                              SerializerProvider provider) throws IOException {

            json.writeStartObject();
            json.writeStringField("version", VERSION.toString());
            json.writeObjectField("gauges", meters(registry, Gauge.class));
            json.writeObjectField("counters", meters(registry, Counter.class));
            json.writeObjectField("functionCounters", meters(registry, FunctionCounter.class));
            json.writeObjectField("timers", meters(registry, Timer.class));
            json.writeObjectField("functionTimers", meters(registry, FunctionTimer.class));
            json.writeObjectField("longTaskTimers", meters(registry, LongTaskTimer.class));
            json.writeObjectField("distributionSummaries", meters(registry, DistributionSummary.class));
            json.writeEndObject();
        }

        private Map<String, List<Meter>> meters(MeterRegistry meterRegistry, Class<? extends Meter> clazz) {
            if (meterRegistry instanceof CompositeMeterRegistry) {
                Map<String, List<Meter>> map = new TreeMap<>();
                ((CompositeMeterRegistry) meterRegistry).getRegistries().forEach(reg ->
                        meters(meterRegistry, clazz).entrySet().forEach(
                                entry -> map.merge(entry.getKey(), entry.getValue(), (m1, m2) ->
                                        Stream.concat(m1.stream(), m2.stream()).collect(Collectors.toList()))
                        ));
                return map;
            }
            return Search.in(meterRegistry).meters().stream()
                    .filter(clazz::isInstance)
                    .collect(Collectors.toMap(
                            meter -> meter.getId().getName(),
                            meter -> Collections.singletonList(meter)));
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
                new TimerSerializer(timeUnit, supportAggregablePercentiles),
                new FunctionTimerSerializer(timeUnit),
                new LongTaskTimerSerializer(timeUnit),
                new DistributionSummarySerializer(timeUnit, supportAggregablePercentiles),
                new MeterRegistrySerializer()
        )));
    }
}
