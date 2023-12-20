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
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("micrometer")
public class MicrometerConsole extends AbstractDevConsole {

    /**
     * Whether to include tags
     */
    public static final String TAGS = "tags";

    public MicrometerConsole() {
        super("camel", "micrometer", "Micrometer", "Display runtime metrics");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final boolean tags = "true".equals(options.getOrDefault(TAGS, "true"));

        StringBuilder sb = new StringBuilder();

        MeterRegistry mr = lookupMeterRegistry();
        sb.append(String.format("MeterRegistry: %s\n\n", mr.getClass().getName()));

        int i = 0;
        for (Meter m : mr.getMeters()) {
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
                sb.append(String.format("    %s: %s\n", name, cnt));
                if (tags) {
                    addTags(sb, c.getId());
                }
            }
        }
        i = 0;
        for (Meter m : mr.getMeters()) {
            if (m instanceof Gauge) {
                Gauge g = (Gauge) m;
                if (i == 0) {
                    sb.append("\nGauges:\n");
                }
                i++;
                String name = g.getId().getName();
                double cnt = g.value();
                sb.append(String.format("    %s: %s\n", name, cnt));
                if (tags) {
                    addTags(sb, g.getId());
                }
            }
        }
        i = 0;
        for (Meter m : mr.getMeters()) {
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
                sb.append(String.format("    %s: %d (total: %dms mean: %dms max: %dms)\n", name, count, total, mean, max));
                if (tags) {
                    addTags(sb, t.getId());
                }
            }
        }
        i = 0;
        for (Meter m : mr.getMeters()) {
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
                        String.format("    %s: %d (duration: %dms mean: %dms max: %dms)\n", name, tasks, duration, mean, max));
                if (tags) {
                    addTags(sb, t.getId());
                }
            }
        }
        i = 0;
        for (Meter m : mr.getMeters()) {
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
                sb.append(String.format("    %s: %d (total: %f mean: %f max: %f)\n", name, count, total, mean, max));
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
            sb.append(String.format("        %s\n", sj));
        }
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        final boolean tags = "true".equals(options.getOrDefault(TAGS, "true"));

        JsonObject root = new JsonObject();

        MeterRegistry mr = lookupMeterRegistry();
        root.put("meterRegistryClass", mr.getClass().getName());

        int i = 0;
        List<JsonObject> list = new ArrayList<>();
        for (Meter m : mr.getMeters()) {
            if (m instanceof Counter) {
                Counter c = (Counter) m;
                if (i == 0) {
                    root.put("counters", list);
                }
                i++;
                JsonObject jo = new JsonObject();
                jo.put("name", c.getId().getName());
                if (c.getId().getDescription() != null) {
                    jo.put("description", c.getId().getDescription());
                }
                if (tags) {
                    addTags(m, jo);
                }
                // strip decimal if counter is integer based
                String cnt = String.valueOf(c.count());
                if (cnt.endsWith(".0") || cnt.endsWith(",0")) {
                    cnt = cnt.substring(0, cnt.length() - 2);
                    jo.put("count", Long.valueOf(cnt));
                } else {
                    // it has decimals so store as-is
                    jo.put("count", c.count());
                }
                list.add(jo);
            }
        }
        list.sort(this::sortByName);
        i = 0;
        list = new ArrayList<>();
        for (Meter m : mr.getMeters()) {
            if (m instanceof Gauge) {
                Gauge g = (Gauge) m;
                if (i == 0) {
                    root.put("gauges", list);
                }
                i++;
                JsonObject jo = new JsonObject();
                jo.put("name", g.getId().getName());
                if (g.getId().getDescription() != null) {
                    jo.put("description", g.getId().getDescription());
                }
                if (tags) {
                    addTags(m, jo);
                }
                jo.put("value", g.value());
                list.add(jo);
            }
        }
        list.sort(this::sortByName);
        i = 0;
        list = new ArrayList<>();
        for (Meter m : mr.getMeters()) {
            if (m instanceof Timer) {
                Timer t = (Timer) m;
                if (i == 0) {
                    root.put("timers", list);
                }
                i++;
                JsonObject jo = new JsonObject();
                jo.put("name", t.getId().getName());
                if (t.getId().getDescription() != null) {
                    jo.put("description", t.getId().getDescription());
                }
                if (tags) {
                    addTags(m, jo);
                }
                jo.put("count", t.count());
                jo.put("mean", Math.round(t.mean(TimeUnit.MILLISECONDS)));
                jo.put("max", Math.round(t.max(TimeUnit.MILLISECONDS)));
                jo.put("total", Math.round(t.totalTime(TimeUnit.MILLISECONDS)));
                list.add(jo);
            }
        }
        list.sort(this::sortByName);
        i = 0;
        list = new ArrayList<>();
        for (Meter m : mr.getMeters()) {
            if (m instanceof LongTaskTimer) {
                LongTaskTimer t = (LongTaskTimer) m;
                if (i == 0) {
                    root.put("longTaskTimers", list);
                }
                i++;
                JsonObject jo = new JsonObject();
                jo.put("name", t.getId().getName());
                if (t.getId().getDescription() != null) {
                    jo.put("description", t.getId().getDescription());
                }
                if (tags) {
                    addTags(m, jo);
                }
                jo.put("activeTasks", t.activeTasks());
                jo.put("mean", Math.round(t.mean(TimeUnit.MILLISECONDS)));
                jo.put("max", Math.round(t.max(TimeUnit.MILLISECONDS)));
                jo.put("duration", Math.round(t.duration(TimeUnit.MILLISECONDS)));
                list.add(jo);
            }
        }
        list.sort(this::sortByName);
        i = 0;
        list = new ArrayList<>();
        for (Meter m : mr.getMeters()) {
            if (m instanceof DistributionSummary) {
                DistributionSummary d = (DistributionSummary) m;
                if (i == 0) {
                    root.put("distribution", list);
                }
                i++;
                JsonObject jo = new JsonObject();
                jo.put("name", d.getId().getName());
                if (d.getId().getDescription() != null) {
                    jo.put("description", d.getId().getDescription());
                }
                if (tags) {
                    addTags(m, jo);
                }
                jo.put("count", d.count());
                jo.put("mean", d.mean());
                jo.put("max", d.max());
                jo.put("totalAmount", d.totalAmount());
                list.add(jo);
            }
        }

        return root;
    }

    private void addTags(Meter m, JsonObject root) {
        List<JsonObject> list = new ArrayList<>();
        for (Tag t : m.getId().getTags()) {
            JsonObject jo = new JsonObject();
            jo.put("key", t.getKey());
            jo.put("value", t.getValue());
            list.add(jo);
        }
        if (!list.isEmpty()) {
            root.put("tags", list);
        }
    }

    private MeterRegistry lookupMeterRegistry() {
        return MicrometerUtils.getOrCreateMeterRegistry(getCamelContext().getRegistry(),
                MicrometerConstants.METRICS_REGISTRY_NAME);
    }

    private int sortByName(JsonObject o1, JsonObject o2) {
        return o1.getString("name").compareToIgnoreCase(o2.getString("name"));
    }

}
