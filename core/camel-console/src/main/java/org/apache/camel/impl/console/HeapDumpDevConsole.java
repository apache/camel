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
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "heap-dump", displayName = "Heap Dump",
            description = "Write a heap dump (.hprof) file for deep memory analysis", readOnly = false)
@Configurer(extended = true)
public class HeapDumpDevConsole extends AbstractDevConsole {

    public record Response(
            @Metadata(description = "The absolute path of the written heap dump file (only present on success)") String file,
            @Metadata(description = "The size in bytes of the written heap dump file (only present on success)") Long size,
            @Metadata(description = "The error message (only present on failure)") String error) {
    }

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
        Response response = buildResponse(options);
        if (response.error() != null) {
            return "Heap dump failed: " + response.error();
        }
        return "Heap dump written to: " + response.file() + " (" + response.size() + " bytes)";
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        return JsonRecordSupport.toJsonObject(buildResponse(options));
    }

    private Response buildResponse(Map<String, Object> options) {
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
            return new Response(file.getAbsolutePath(), file.length(), null);
        } catch (Exception e) {
            return new Response(null, null, e.getMessage());
        }
    }
}
