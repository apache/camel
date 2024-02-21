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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("log")
public class LogDevConsole extends AbstractDevConsole {

    // log4j support
    private static final String LOG4J_MBEAN = "org.apache.logging.log4j2";

    public LogDevConsole() {
        super("camel", "log", "Log", "Logging framework");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        Map<String, String> levels = fetchLoggingLevels();
        if (!levels.isEmpty()) {
            sb.append("Logging Levels:\n");
            levels.forEach((k, v) -> sb.append(String.format("\n    %s = %s", k, v)));
        }
        sb.append("\n");

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        Map<String, String> levels = fetchLoggingLevels();
        if (!levels.isEmpty()) {
            JsonObject props = new JsonObject();
            root.put("levels", props);
            props.putAll(levels);
        }
        return root;
    }

    private static Map<String, String> fetchLoggingLevels() {
        Map<String, String> levels = new TreeMap<>();
        try {
            MBeanServer ms = ManagementFactory.getPlatformMBeanServer();
            if (ms != null) {
                Set<ObjectName> set = ms.queryNames(new ObjectName(LOG4J_MBEAN + ":type=*,component=Loggers,name=*"), null);
                for (ObjectName on : set) {
                    if (ms.isRegistered(on)) {
                        String name = (String) ms.getAttribute(on, "Name");
                        String level = (String) ms.getAttribute(on, "Level");
                        if (name == null || name.isEmpty()) {
                            name = "root";
                        }
                        levels.put(name, level);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return levels;
    }
}
