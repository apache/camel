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
package org.apache.camel.component.microprofile.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import org.eclipse.microprofile.metrics.MetricRegistry;

@DevConsole("microprofile-metrics")
public class MicroProfileConsole extends AbstractDevConsole {

    public MicroProfileConsole() {
        super("camel", "microprofile-metrics", "MicroProfile Metrics", "Display runtime metrics");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        MetricRegistry mr = getCamelContext().getRegistry().findSingleByType(MetricRegistry.class);
        if (mr != null) {
            sb.append("Counters:\n");
            mr.getCounters().forEach((id, c) -> {
                String name = id.getName();
                long cnt = c.getCount();
                sb.append(String.format("    %s: %s\n", name, cnt));
            });
            sb.append("\nConcurrentGauges:\n");
            mr.getConcurrentGauges().forEach((id, c) -> {
                String name = id.getName();
                long cnt = c.getCount();
                long min = c.getMin();
                long max = c.getMax();
                sb.append(String.format("    %s: %s (min:%s max:%s)\n", name, cnt, max, min));
            });
            sb.append("\nGauges:\n");
            mr.getGauges().forEach((id, c) -> {
                String name = id.getName();
                Object val = c.getValue();
                sb.append(String.format("    %s: %s\n", name, val));
            });
            sb.append("\nHistograms:\n");
            mr.getHistograms().forEach((id, c) -> {
                String name = id.getName();
                long cnt = c.getCount();
                long sum = c.getSum();
                sb.append(String.format("    %s: %s (sum: %s)\n", name, cnt, sum));
            });
            sb.append("\nMeters:\n");
            mr.getMeters().forEach((id, c) -> {
                String name = id.getName();
                long cnt = c.getCount();
                double mean = c.getMeanRate();
                double r1 = c.getOneMinuteRate();
                double r5 = c.getFiveMinuteRate();
                double r15 = c.getFifteenMinuteRate();
                sb.append(String.format("    %s: %s (mean: %f 1min: %f 5min: %f 15min: %f)\n", name, cnt, mean, r1, r5, r15));
            });
            sb.append("\nSimpleTimers:\n");
            mr.getSimpleTimers().forEach((id, c) -> {
                String name = id.getName();
                long cnt = c.getCount();
                String dur = TimeUtils.printDuration(c.getElapsedTime(), true);
                sb.append(String.format("    %s: %s (elapsed: %s)\n", name, cnt, dur));
            });
            sb.append("\nTimers:\n");
            mr.getTimers().forEach((id, c) -> {
                String name = id.getName();
                long cnt = c.getCount();
                String dur = TimeUtils.printDuration(c.getElapsedTime(), true);
                double mean = c.getMeanRate();
                double r1 = c.getOneMinuteRate();
                double r5 = c.getFiveMinuteRate();
                double r15 = c.getFifteenMinuteRate();
                sb.append(String.format("    %s: %s (elapsed: %s mean: %f 1m: %f 5m: %f 15m: %f)\n", name, cnt, dur, mean, r1,
                        r5, r15));
            });
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        MetricRegistry mr = getCamelContext().getRegistry().findSingleByType(MetricRegistry.class);
        if (mr != null) {
            final List<JsonObject> list = new ArrayList<>();
            root.put("counters", list);
            mr.getCounters().forEach((id, c) -> {
                JsonObject jo = new JsonObject();
                jo.put("name", id.getName());
                jo.put("count", c.getCount());
                list.add(jo);
            });
            final List<JsonObject> list2 = new ArrayList<>();
            root.put("concurrentGauges", list2);
            mr.getConcurrentGauges().forEach((id, c) -> {
                JsonObject jo = new JsonObject();
                jo.put("name", id.getName());
                jo.put("count", c.getCount());
                jo.put("max", c.getMax());
                jo.put("min", c.getMin());
                list2.add(jo);
            });
            final List<JsonObject> list3 = new ArrayList<>();
            root.put("gauges", list3);
            mr.getGauges().forEach((id, c) -> {
                JsonObject jo = new JsonObject();
                jo.put("name", id.getName());
                jo.put("value", c.getValue());
                list3.add(jo);
            });
            final List<JsonObject> list4 = new ArrayList<>();
            root.put("histograms", list4);
            mr.getHistograms().forEach((id, c) -> {
                JsonObject jo = new JsonObject();
                jo.put("name", id.getName());
                jo.put("count", c.getCount());
                jo.put("sum", c.getSum());
                list4.add(jo);
            });
            final List<JsonObject> list5 = new ArrayList<>();
            root.put("meters", list5);
            mr.getMeters().forEach((id, c) -> {
                JsonObject jo = new JsonObject();
                jo.put("name", id.getName());
                jo.put("count", c.getCount());
                jo.put("meanRate", c.getMeanRate());
                jo.put("rate1minute", c.getOneMinuteRate());
                jo.put("rate5minute", c.getFiveMinuteRate());
                jo.put("rate15minute", c.getFifteenMinuteRate());
                list5.add(jo);
            });
            final List<JsonObject> list6 = new ArrayList<>();
            root.put("simpleTimers", list6);
            mr.getSimpleTimers().forEach((id, c) -> {
                JsonObject jo = new JsonObject();
                jo.put("name", id.getName());
                jo.put("count", c.getCount());
                jo.put("elapsedTimeMillis", c.getElapsedTime().toMillis());
                list6.add(jo);
            });
            final List<JsonObject> list7 = new ArrayList<>();
            root.put("timers", list7);
            mr.getTimers().forEach((id, c) -> {
                JsonObject jo = new JsonObject();
                jo.put("name", id.getName());
                jo.put("count", c.getCount());
                jo.put("elapsedTimeMillis", c.getElapsedTime().toMillis());
                jo.put("meanRate", c.getMeanRate());
                jo.put("rate1minute", c.getOneMinuteRate());
                jo.put("rate5minute", c.getFiveMinuteRate());
                jo.put("rate15minute", c.getFifteenMinuteRate());
                list7.add(jo);
            });
        }

        return root;
    }
}
