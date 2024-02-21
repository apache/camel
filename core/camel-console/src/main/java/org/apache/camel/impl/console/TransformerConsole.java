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

import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole("transformers")
public class TransformerConsole extends AbstractDevConsole {

    public TransformerConsole() {
        super("camel", "transformers", "Data Type Transformers", "Camel Data Type Transformer information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        TransformerRegistry<?> reg = getCamelContext().getTransformerRegistry();
        sb.append(String.format("\n    Size: %s", reg.size()));
        sb.append(String.format("\n    Dynamic Size: %s", reg.dynamicSize()));
        sb.append(String.format("\n    Static Size: %s", reg.staticSize()));
        sb.append(String.format("\n    Maximum Cache Size: %s", reg.getMaximumCacheSize()));

        sb.append("\n");
        sb.append("\nTransformers:\n");
        for (Map.Entry<?, Transformer> entry : reg.entrySet()) {
            Transformer t = entry.getValue();
            String from = t.getFrom() != null ? t.getFrom().getFullName() : "*";
            String to = t.getTo() != null ? t.getTo().getFullName() : "*";
            sb.append(String.format("\n        %s (from: %s to: %s)", t.getName(), from, to));
        }
        sb.append("\n");

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        TransformerRegistry<?> reg = getCamelContext().getTransformerRegistry();
        root.put("size", reg.size());
        root.put("dynamicSize", reg.dynamicSize());
        root.put("staticSize", reg.staticSize());
        root.put("maximumCacheSize", reg.getMaximumCacheSize());
        JsonArray arr = new JsonArray();
        for (Map.Entry<?, Transformer> entry : reg.entrySet()) {
            Transformer t = entry.getValue();
            String from = t.getFrom() != null ? t.getFrom().getFullName() : null;
            String to = t.getTo() != null ? t.getTo().getFullName() : null;
            JsonObject jo = new JsonObject();
            jo.put("name", t.getName());
            if (from != null) {
                jo.put("from", from);
            }
            if (to != null) {
                jo.put("to", to);
            }
            arr.add(jo);
        }
        if (!arr.isEmpty()) {
            root.put("transformers", arr);
        }
        return root;
    }
}
