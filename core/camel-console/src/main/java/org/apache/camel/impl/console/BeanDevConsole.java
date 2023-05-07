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
import java.util.stream.Stream;

import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("bean")
public class BeanDevConsole extends AbstractDevConsole {

    public BeanDevConsole() {
        super("camel", "bean", "Bean", "Displays Java beans from the registry");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        Map<String, Object> beans = getCamelContext().getRegistry().findByTypeWithName(Object.class);
        Stream<String> keys = beans.keySet().stream().sorted(String::compareToIgnoreCase);
        keys.forEach(k -> {
            String v = beans.getOrDefault(k, "<null>").getClass().getName();
            sb.append(String.format("    %s (class: %s)%n", k, v));
        });

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        JsonObject jo = new JsonObject();
        root.put("beans", jo);
        Map<String, Object> beans = getCamelContext().getRegistry().findByTypeWithName(Object.class);
        Stream<String> keys = beans.keySet().stream().sorted(String::compareToIgnoreCase);
        keys.forEach(k -> {
            String v = beans.getOrDefault(k, "null").getClass().getName();
            beans.put(k, v);
        });

        return root;
    }
}
