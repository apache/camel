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
package org.apache.camel.component.micrometer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "micrometer", description = "Display runtime metrics")
public class MicrometerConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "Whether to include tags", defaultValue = "true",
              javaType = "java.lang.Boolean")
    public static final String TAGS = "tags";

    @Metadata(label = "query", description = "Filters matching metrics by name",
              javaType = "java.lang.String")
    public static final String FILTER = "filter";

    public record TagEntry(String key, String value) {
    }

    public record CounterEntry(
            String name, String description, List<TagEntry> tags,
            @Metadata(description = "The counter value (integer when whole, decimal otherwise)") Object count) {
    }

    public record GaugeEntry(String name, String description, List<TagEntry> tags, double value) {
    }

    public record TimerEntry(
            String name, String description, List<TagEntry> tags, long count, long mean, long max, long total) {
    }

    public record LongTaskTimerEntry(
            String name, String description, List<TagEntry> tags, int activeTasks, long mean, long max,
            long duration) {
    }

    public record DistributionEntry(
            String name, String description, List<TagEntry> tags, long count, double mean, double max,
            double totalAmount) {
    }

    public record Response(
            String meterRegistryClass,
            @Metadata(description = "Only present when there are any counters") List<CounterEntry> counters,
            @Metadata(description = "Only present when there are any gauges") List<GaugeEntry> gauges,
            @Metadata(description = "Only present when there are any timers") List<TimerEntry> timers,
            @Metadata(description = "Only present when there are any long task timers") List<LongTaskTimerEntry> longTaskTimers,
            @Metadata(description = "Only present when there are any distribution summaries") List<DistributionEntry> distribution) {
    }

    public MicrometerConsole() {
        super("camel", "micrometer", "Micrometer", "Display runtime metrics");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final boolean tags = optionBoolean(options, TAGS, true);
        final String filter = optionString(options, FILTER);

        StringBuilder sb = new StringBuilder();

        MeterRegistry mr = lookupMeterRegistry();
        sb.append(String.format("MeterRegistry: %s%n%n", mr.getClass().getName()));

        List<Meter> meters = mr.getMeters()
                .stream()
                .filter(meter -> accept(meter.getId().getName(), filter))
                .toList();

        int i = 0;
        for (Meter m : meters) {
            if (m instanceof Counter) {
                Counter c = (Counter) m;
                if (i == 0) {
                    sb.append("Counters:\n");
                }
                i++;
                String name = c.getId().getName();
                String cnt = String.valueOf(c.count());
                // strip decimal if counter is integer based
                if (cnt.endsWith(".0") || cnt.endsWith(",0")) {
                    cnt = cnt.substring(0, cnt.length() - 2);
                }
                sb.append(String.format("    %s: %s%n", name, cnt));
                if (tags) {
                    addTags(sb, c.getId());
                }
            }
        }
        i = 0;
        for (Meter m : meters) {
            if (m instanceof Gauge) {
                Gauge g = (Gauge) m;
                if (i == 0) {
                    sb.append("\nGauges:\n");
                }
                i++;
                String name = g.getId().getName();
                double cnt = g.value();
                sb.append(String.format("    %s: %s%n", name, cnt));
                if (tags) {
                    addTags(sb, g.getId());
                }
            }
        }
        i = 0;
        for (Meter m : meters) {
            if (m instanceof Timer) {
                Timer t = (Timer) m;
                if (i == 0) {
                    sb.append("\nTimer:\n");
                }
                i++;
                String name = t.getId().getName();
                long count = t.count();
                long mean = Math.round(t.mean(TimeUnit.MILLISECONDS));
                long max = Math.round(t.max(TimeUnit.MILLISECONDS));
                long total = Math.round(t.totalTime(TimeUnit.MILLISECONDS));
                sb.append(String.format("    %s: %d (total: %dms mean: %dms max: %dms)%n", name, count, total, mean, max));
                if (tags) {
                    addTags(sb, t.getId());
                }
            }
        }
        i = 0;
        for (Meter m : meters) {
            if (m instanceof LongTaskTimer) {
                LongTaskTimer t = (LongTaskTimer) m;
                if (i == 0) {
                    sb.append("\nLongTaskTimer:\n");
                }
                i++;
                String name = t.getId().getName();
                int tasks = t.activeTasks();
                long mean = Math.round(t.mean(TimeUnit.MILLISECONDS));
                long max = Math.round(t.max(TimeUnit.MILLISECONDS));
                long duration = Math.round(t.duration(TimeUnit.MILLISECONDS));
                sb.append(
                        String.format("    %s: %d (duration: %dms mean: %dms max: %dms)%n", name, tasks, duration, mean, max));
                if (tags) {
                    addTags(sb, t.getId());
                }
            }
        }
        i = 0;
        for (Meter m : meters) {
            if (m instanceof DistributionSummary) {
                DistributionSummary d = (DistributionSummary) m;
                if (i == 0) {
                    sb.append("\nDistributionSummary:\n");
                }
                i++;
                String name = d.getId().getName();
                long count = d.count();
                double mean = d.mean();
                double max = d.max();
                double total = d.totalAmount();
                sb.append(String.format("    %s: %d (total: %f mean: %f max: %f)%n", name, count, total, mean, max));
                if (tags) {
                    addTags(sb, d.getId());
                }
            }
        }

        return sb.toString();
    }

    protected void addTags(StringBuilder sb, Meter.Id id) {
        StringJoiner sj = new StringJoiner(" ");
        for (Tag tag : id.getTags()) {
            sj.add(tag.getKey() + "=" + tag.getValue());
        }
        if (sj.length() > 0) {
            sb.append(String.format("        %s%n", sj));
        }
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        final boolean tags = optionBoolean(options, TAGS, true);
        final String filter = optionString(options, FILTER);

        MeterRegistry mr = lookupMeterRegistry();

        List<Meter> meters = mr.getMeters()
                .stream()
                .filter(meter -> accept(meter.getId().getName(), filter))
                .toList();

        List<CounterEntry> counters = new ArrayList<>();
        for (Meter m : meters) {
            if (m instanceof Counter c) {
                // strip decimal if counter is integer based
                String cnt = String.valueOf(c.count());
                Object count;
                if (cnt.endsWith(".0") || cnt.endsWith(",0")) {
                    count = Long.valueOf(cnt.substring(0, cnt.length() - 2));
                } else {
                    // it has decimals so store as-is
                    count = c.count();
                }
                counters.add(new CounterEntry(
                        c.getId().getName(), c.getId().getDescription(), buildTags(tags, m), count));
            }
        }
        counters.sort(Comparator.comparing(CounterEntry::name, String.CASE_INSENSITIVE_ORDER));

        List<GaugeEntry> gauges = new ArrayList<>();
        for (Meter m : meters) {
            if (m instanceof Gauge g) {
                gauges.add(new GaugeEntry(g.getId().getName(), g.getId().getDescription(), buildTags(tags, m), g.value()));
            }
        }
        gauges.sort(Comparator.comparing(GaugeEntry::name, String.CASE_INSENSITIVE_ORDER));

        List<TimerEntry> timers = new ArrayList<>();
        for (Meter m : meters) {
            if (m instanceof Timer t) {
                timers.add(new TimerEntry(
                        t.getId().getName(), t.getId().getDescription(), buildTags(tags, m), t.count(),
                        Math.round(t.mean(TimeUnit.MILLISECONDS)), Math.round(t.max(TimeUnit.MILLISECONDS)),
                        Math.round(t.totalTime(TimeUnit.MILLISECONDS))));
            }
        }
        timers.sort(Comparator.comparing(TimerEntry::name, String.CASE_INSENSITIVE_ORDER));

        List<LongTaskTimerEntry> longTaskTimers = new ArrayList<>();
        for (Meter m : meters) {
            if (m instanceof LongTaskTimer t) {
                longTaskTimers.add(new LongTaskTimerEntry(
                        t.getId().getName(), t.getId().getDescription(), buildTags(tags, m), t.activeTasks(),
                        Math.round(t.mean(TimeUnit.MILLISECONDS)), Math.round(t.max(TimeUnit.MILLISECONDS)),
                        Math.round(t.duration(TimeUnit.MILLISECONDS))));
            }
        }
        longTaskTimers.sort(Comparator.comparing(LongTaskTimerEntry::name, String.CASE_INSENSITIVE_ORDER));

        List<DistributionEntry> distribution = new ArrayList<>();
        for (Meter m : meters) {
            if (m instanceof DistributionSummary d) {
                distribution.add(new DistributionEntry(
                        d.getId().getName(), d.getId().getDescription(), buildTags(tags, m), d.count(), d.mean(),
                        d.max(), d.totalAmount()));
            }
        }

        Response response = new Response(
                mr.getClass().getName(), counters.isEmpty() ? null : counters, gauges.isEmpty() ? null : gauges,
                timers.isEmpty() ? null : timers, longTaskTimers.isEmpty() ? null : longTaskTimers,
                distribution.isEmpty() ? null : distribution);
        return JsonRecordSupport.toJsonObject(response);
    }

    private static List<TagEntry> buildTags(boolean tags, Meter m) {
        if (!tags) {
            return null;
        }
        List<TagEntry> list = new ArrayList<>();
        for (Tag t : m.getId().getTags()) {
            list.add(new TagEntry(t.getKey(), t.getValue()));
        }
        return list.isEmpty() ? null : list;
    }

    private MeterRegistry lookupMeterRegistry() {
        return MicrometerUtils.getOrCreateMeterRegistry(getCamelContext().getRegistry(),
                MicrometerConstants.METRICS_REGISTRY_NAME);
    }

    private static boolean accept(String name, String filter) {
        if (ObjectHelper.isEmpty(filter)) {
            return true;
        }

        return PatternHelper.matchPattern(name, filter);
    }
}
