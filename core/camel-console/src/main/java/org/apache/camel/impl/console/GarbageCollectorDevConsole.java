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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole("gc")
@Configurer(bootstrap = true)
public class GarbageCollectorDevConsole extends AbstractDevConsole {

    public GarbageCollectorDevConsole() {
        super("jvm", "gc", "Garbage Collector", "Displays Garbage Collector information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        if (gcs != null && !gcs.isEmpty()) {
            for (GarbageCollectorMXBean gc : gcs) {
                sb.append(String.format("\n    %s: %s (%s ms)", gc.getName(), gc.getCollectionCount(), gc.getCollectionTime()));
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        root.put("garbageCollectors", arr);

        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        if (gcs != null && !gcs.isEmpty()) {
            for (GarbageCollectorMXBean gc : gcs) {
                JsonObject jo = new JsonObject();
                arr.add(jo);
                jo.put("name", gc.getName());
                jo.put("collectionCount", gc.getCollectionCount());
                jo.put("collectionTime", gc.getCollectionTime());
                jo.put("memoryPoolNames", String.join(",", Arrays.asList(gc.getMemoryPoolNames())));
            }
        }
        return root;
    }
}
