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
import java.util.Set;

import org.apache.camel.spi.BrowsableVariableRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "variables", description = "Displays variables")
public class VariablesDevConsole extends AbstractDevConsole {

    public record VariableEntry(
            @Metadata(description = "The variable name") String key,
            @Metadata(description = "The variable value type (only present when the value is known)") String type,
            @Metadata(description = "The variable value (only present when known)") Object value) {
    }

    public record RepositoryEntry(
            @Metadata(description = "The variable repository id") String id,
            @Metadata(description = "The variables in this repository") List<VariableEntry> variables) {
    }

    public record Response(
            @Metadata(description = "The variable repositories (only present when there are any with variables)") List<RepositoryEntry> repositories) {
    }

    public VariablesDevConsole() {
        super("camel", "variables", "Variables", "Displays variables");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        Set<BrowsableVariableRepository> repos = getCamelContext().getRegistry().findByType(BrowsableVariableRepository.class);
        for (BrowsableVariableRepository repo : repos) {
            sb.append("\n");
            sb.append(String.format("Repository: %s (size: %d)", repo.getId(), repo.size()));
            for (Map.Entry<String, Object> entry : repo.getVariables().entrySet()) {
                String k = entry.getKey();
                Object v = entry.getValue();
                String t = v != null ? v.getClass().getName() : "<null>";
                sb.append(String.format("%n    %s (%s) = %s", k, t, v));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<RepositoryEntry> repositories = new ArrayList<>();

        Set<BrowsableVariableRepository> repos = getCamelContext().getRegistry().findByType(BrowsableVariableRepository.class);
        for (BrowsableVariableRepository repo : repos) {
            final List<VariableEntry> entries = toEntries(repo);
            if (!entries.isEmpty()) {
                repositories.add(new RepositoryEntry(repo.getId(), entries));
            }
        }

        Response response = new Response(repositories.isEmpty() ? null : repositories);
        return JsonRecordSupport.toJsonObject(response);
    }

    private static List<VariableEntry> toEntries(BrowsableVariableRepository repo) {
        List<VariableEntry> arr = new ArrayList<>();
        for (Map.Entry<String, Object> entry : repo.getVariables().entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();
            String type = v != null ? v.getClass().getName() : null;
            Object value = null;
            if (type != null) {
                value = Jsoner.trySerialize(v);
                if (value == null) {
                    // cannot serialize so escape
                    value = Jsoner.escape(v.toString());
                } else {
                    // okay so use the value as-s
                    value = v;
                }
            }

            arr.add(new VariableEntry(k, type, value));
        }
        return arr;
    }
}
