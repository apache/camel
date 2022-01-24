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
import org.apache.camel.util.TimeUtils;

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
    protected Object doCall(MediaType mediaType, Map<String, Object> options) {
        // only text is supported
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
                String cp = String.join("\n    ", mb.getInputArguments());
                sb.append("\n    ").append(cp).append("\n");
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
}
