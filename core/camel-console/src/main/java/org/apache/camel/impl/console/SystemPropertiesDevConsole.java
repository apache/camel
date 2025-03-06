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

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "system-properties", description = "Displays Java System Properties")
@Configurer(extended = true)
public class SystemPropertiesDevConsole extends AbstractDevConsole {

    public SystemPropertiesDevConsole() {
        super("jvm", "system-properties", "Java System Properties", "Displays Java System Properties");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        Properties p = System.getProperties();
        Set<String> keys = new TreeSet<>(p.stringPropertyNames());

        sb.append("System Properties:");
        sb.append("\n");
        for (String k : keys) {
            sb.append(String.format("    %s = %s\n", k, System.getProperty(k)));
        }
        sb.append("\n");

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        JsonArray arr = new JsonArray();
        root.put("systemProperties", arr);

        Properties p = System.getProperties();
        Set<String> keys = new TreeSet<>(p.stringPropertyNames());
        for (String k : keys) {
            JsonObject jo = new JsonObject();
            jo.put(k, System.getProperty(k));
            arr.add(jo);
        }

        return root;
    }

}
