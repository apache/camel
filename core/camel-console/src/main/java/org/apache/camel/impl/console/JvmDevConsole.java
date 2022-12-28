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
import java.lang.management.RuntimeMXBean;
import java.util.Map;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

@DevConsole("jvm")
@Configurer(bootstrap = true)
public class JvmDevConsole extends AbstractDevConsole {

    @Metadata(defaultValue = "true", description = "Show classpath information")
    private boolean showClasspath = true;

    public JvmDevConsole() {
        super("jvm", "jvm", "JVM", "Displays JVM information");
    }

    public boolean isShowClasspath() {
        return showClasspath;
    }

    public void setShowClasspath(boolean showClasspath) {
        this.showClasspath = showClasspath;
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        RuntimeMXBean mb = ManagementFactory.getRuntimeMXBean();
        if (mb != null) {
            sb.append(String.format("Java Name: %s\n", mb.getVmName()));
            sb.append(String.format("Java Version: %s\n", mb.getVmVersion()));
            sb.append(String.format("Java Vendor: %s\n", mb.getVmVendor()));
            sb.append(String.format("Uptime: %s\n", TimeUtils.printDuration(mb.getUptime())));
            sb.append(String.format("PID: %s\n", mb.getPid()));
            if (!mb.getInputArguments().isEmpty()) {
                sb.append("Input Arguments:");
                String arg = String.join("\n    ", mb.getInputArguments());
                sb.append("\n    ").append(arg).append("\n");
            }
            if (mb.isBootClassPathSupported()) {
                sb.append("Boot Classpath:");
                String cp = String.join("\n    ", mb.getBootClassPath().split("[:|;]"));
                sb.append("\n    ").append(cp).append("\n");
            }
            sb.append("Classpath:");
            String cp = String.join("\n    ", mb.getClassPath().split("[:|;]"));
            sb.append("\n    ").append(cp).append("\n");
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        RuntimeMXBean mb = ManagementFactory.getRuntimeMXBean();
        if (mb != null) {
            root.put("vmName", mb.getVmName());
            root.put("vmVersion", mb.getVmVersion());
            root.put("vmVendor", mb.getVmVendor());
            root.put("vmUptime", TimeUtils.printDuration(mb.getUptime()));
            root.put("pid", mb.getPid());
            if (!mb.getInputArguments().isEmpty()) {
                String arg = String.join(" ", mb.getInputArguments());
                root.put("inputArguments", arg);
            }
            if (mb.isBootClassPathSupported()) {
                String[] cp = mb.getBootClassPath().split("[:|;]");
                root.put("bootClasspath", cp);
            }
            String[] cp = mb.getClassPath().split("[:|;]");
            root.put("classpath", cp);
        }

        return root;
    }
}
