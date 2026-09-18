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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "transformers", displayName = "Data Type Transformers", description = "Data-type transformer information")
public class TransformerConsole extends AbstractDevConsole {

    public record TransformerEntry(
            @Metadata(description = "The transformer name") String name,
            @Metadata(description = "The from data type (only present when not any-type)") String from,
            @Metadata(description = "The to data type (only present when not any-type)") String to) {
    }

    public record Response(
            @Metadata(description = "Total number of transformers") int size,
            @Metadata(description = "Number of transformers in the dynamic registry") int dynamicSize,
            @Metadata(description = "Number of transformers in the static registry") int staticSize,
            @Metadata(description = "Maximum number of entries to store in the dynamic registry") int maximumCacheSize,
            @Metadata(description = "The transformers (only present when there are any)") List<TransformerEntry> transformers) {
    }

    public TransformerConsole() {
        super("camel", "transformers", "Data Type Transformers", "Data-type transformer information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        TransformerRegistry reg = getCamelContext().getTransformerRegistry();
        sb.append(String.format("%n    Size: %s", reg.size()));
        sb.append(String.format("%n    Dynamic Size: %s", reg.dynamicSize()));
        sb.append(String.format("%n    Static Size: %s", reg.staticSize()));
        sb.append(String.format("%n    Maximum Cache Size: %s", reg.getMaximumCacheSize()));

        sb.append("\n");
        sb.append("\nTransformers:\n");
        for (Map.Entry<?, Transformer> entry : reg.entrySet()) {
            Transformer t = entry.getValue();
            String from = t.getFrom() != null ? t.getFrom().getFullName() : "*";
            String to = t.getTo() != null ? t.getTo().getFullName() : "*";
            sb.append(String.format("%n        %s (from: %s to: %s)", t.getName(), from, to));
        }
        sb.append("\n");

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        TransformerRegistry reg = getCamelContext().getTransformerRegistry();
        List<TransformerEntry> entries = toEntries(reg);

        Response response = new Response(
                reg.size(), reg.dynamicSize(), reg.staticSize(), reg.getMaximumCacheSize(),
                entries.isEmpty() ? null : entries);
        return JsonRecordSupport.toJsonObject(response);
    }

    private static List<TransformerEntry> toEntries(TransformerRegistry reg) {
        List<TransformerEntry> entries = new ArrayList<>();
        for (Map.Entry<?, Transformer> entry : reg.entrySet()) {
            Transformer t = entry.getValue();
            String from = t.getFrom() != null ? t.getFrom().getFullName() : null;
            String to = t.getTo() != null ? t.getTo().getFullName() : null;
            entries.add(new TransformerEntry(t.getName(), from, to));
        }
        return entries;
    }
}
