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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "heap-histogram", displayName = "Heap Histogram", description = "Displays class-level heap memory usage")
@Configurer(extended = true)
public class HeapHistogramDevConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "Limits the number of classes displayed",
              defaultValue = "100", javaType = "java.lang.Integer")
    public static final String LIMIT = "limit";

    public record ClassEntry(
            @Metadata(description = "The class number") int num,
            @Metadata(description = "Number of instances") long instances,
            @Metadata(description = "Number of bytes used") long bytes,
            @Metadata(description = "The class name") String className) {
    }

    public record Response(
            @Metadata(description = "The classes in the histogram") List<ClassEntry> classes,
            @Metadata(description = "Total number of instances") long totalInstances,
            @Metadata(description = "Total number of bytes") long totalBytes) {
    }

    public HeapHistogramDevConsole() {
        super("jvm", "heap-histogram", "Heap Histogram", "Displays class-level heap memory usage");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String histogram = invokeGcClassHistogram();
        if (histogram == null) {
            return "Heap histogram not available (DiagnosticCommand MBean not found)";
        }
        int limit = getLimit(options);
        if (limit > 0) {
            return truncateText(histogram, limit);
        }
        return histogram;
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String histogram = invokeGcClassHistogram();
        Response response;
        if (histogram == null) {
            response = new Response(new ArrayList<>(), 0, 0);
        } else {
            int limit = getLimit(options);
            response = parseHistogram(histogram, limit);
        }
        return JsonRecordSupport.toJsonObject(response);
    }

    private int getLimit(Map<String, Object> options) {
        return optionInt(options, LIMIT, 100);
    }

    private static String invokeGcClassHistogram() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("com.sun.management:type=DiagnosticCommand");
            String result = (String) server.invoke(
                    name, "gcClassHistogram",
                    new Object[] { null },
                    new String[] { String[].class.getName() });
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private static Response parseHistogram(String histogram, int limit) {
        List<ClassEntry> arr = new ArrayList<>();

        long totalInstances = 0;
        long totalBytes = 0;
        int count = 0;

        String[] lines = histogram.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("-") || line.startsWith("num")) {
                continue;
            }
            if (line.startsWith("Total")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    totalInstances = parseLong(parts[1]);
                    totalBytes = parseLong(parts[2]);
                }
                continue;
            }

            String[] parts = line.split("\\s+");
            if (parts.length < 4) {
                continue;
            }

            // parts[0]=num: parts[1]=#instances parts[2]=#bytes parts[3]=class
            String numStr = parts[0].replace(":", "");
            int num = (int) parseLong(numStr);
            long instances = parseLong(parts[1]);
            long bytes = parseLong(parts[2]);
            String className = parts[3];

            if (limit > 0 && count >= limit) {
                continue;
            }

            arr.add(new ClassEntry(num, instances, bytes, StringHelper.readableClassName(className)));
            count++;
        }

        return new Response(arr, totalInstances, totalBytes);
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String truncateText(String histogram, int limit) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        String[] lines = histogram.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("-") || trimmed.startsWith("num")) {
                sb.append(line).append('\n');
                continue;
            }
            if (trimmed.startsWith("Total")) {
                sb.append(line).append('\n');
                continue;
            }
            if (count < limit) {
                sb.append(line).append('\n');
                count++;
            }
        }
        return sb.toString();
    }
}
