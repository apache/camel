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

import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("properties")
public class PropertiesDevConsole extends AbstractDevConsole {

    public PropertiesDevConsole() {
        super("camel", "properties", "Properties", "Displays the properties loaded by Camel");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        PropertiesComponent pc = getCamelContext().getPropertiesComponent();
        String loc = String.join(", ", pc.getLocations());
        sb.append(String.format("Properties loaded from locations: %s", loc));
        sb.append("\n");
        for (Map.Entry<Object, Object> entry : pc.loadProperties().entrySet()) {
            Object k = entry.getKey();
            Object v = entry.getValue();
            sb.append(String.format("\n    %s = %s", k, v));
        }
        sb.append("\n");

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        PropertiesComponent pc = getCamelContext().getPropertiesComponent();
        root.put("locations", pc.getLocations());
        JsonObject props = new JsonObject();
        root.put("properties", props);
        for (Map.Entry<Object, Object> entry : pc.loadProperties().entrySet()) {
            String k = entry.getKey().toString();
            Object v = entry.getValue();
            props.put(k, v);
        }

        return root;
    }
}
