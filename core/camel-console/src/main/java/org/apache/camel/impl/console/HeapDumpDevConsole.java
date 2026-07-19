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

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "heap-dump", displayName = "Heap Dump",
            description = "Write a heap dump (.hprof) file for deep memory analysis")
@Configurer(extended = true)
public class HeapDumpDevConsole extends AbstractDevConsole {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Metadata(label = "query", description = "File name for the heap dump (without .hprof extension)",
              javaType = "java.lang.String")
    public static final String NAME = "name";

    @Metadata(label = "query", description = "Whether to dump only live objects (default true)",
              defaultValue = "true", javaType = "java.lang.Boolean")
    public static final String LIVE = "live";

    public HeapDumpDevConsole() {
        super("jvm", "heap-dump", "Heap Dump", "Write a heap dump (.hprof) file for deep memory analysis");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        JsonObject json = doCallJson(options);
        String error = json.getString("error");
        if (error != null) {
            return "Heap dump failed: " + error;
        }
        return "Heap dump written to: " + json.getString("file") + " (" + json.getLong("size") + " bytes)";
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        String name = optionString(options, NAME);
        if (name == null || name.isBlank()) {
            name = "heap-dump-" + TIMESTAMP.format(LocalDateTime.now());
        }
        // strip path separators to prevent writing outside the working directory
        name = Path.of(name).getFileName().toString();
        if (!name.endsWith(".hprof")) {
            name = name + ".hprof";
        }

        boolean live = optionBoolean(options, LIVE, true);

        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName objName = new ObjectName("com.sun.management:type=HotSpotDiagnostic");
            server.invoke(objName, "dumpHeap",
                    new Object[] { name, live },
                    new String[] { String.class.getName(), boolean.class.getName() });

            File file = new File(name);
            root.put("file", file.getAbsolutePath());
            root.put("size", file.length());
        } catch (Exception e) {
            root.put("error", e.getMessage());
        }

        return root;
    }
}
