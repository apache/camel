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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

public abstract class InfraBaseCommand extends CamelCommand {

    protected final ObjectMapper jsonMapper = new ObjectMapper();

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    public InfraBaseCommand(CamelJBangMain main) {
        super(main);

        jsonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        jsonMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        jsonMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        jsonMapper.configure(MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_OPTIONALS, false);
    }

    protected List<TestInfraService> getMetadata() throws IOException {
        List<TestInfraService> metadata;

        CamelCatalog catalog = new DefaultCamelCatalog();
        try (InputStream is
                = catalog.loadResource("test-infra", "metadata.json")) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            metadata = jsonMapper.readValue(json, new TypeReference<List<TestInfraService>>() {
            });
        }

        return metadata;
    }

    public int listServices(Consumer<List<Row>> serviceConsumer) throws IOException {
        Map<String, InfraServiceAlias> services = new LinkedHashMap<>();

        List<TestInfraService> metadata = getMetadata();

        List<InfraList.Row> rows = new ArrayList<>(metadata.size());

        for (TestInfraService service : metadata) {
            for (String alias : service.alias()) {
                if (!services.containsKey(alias)) {
                    services.put(alias, new InfraServiceAlias(service.description()));
                }

                if (service.aliasImplementation() != null) {
                    services.get(alias).getAliasImplementation().addAll(service.aliasImplementation());
                }
            }
        }

        int width = 0;
        for (Map.Entry<String, InfraServiceAlias> entry : services.entrySet()) {
            width = Math.max(width, entry.getKey().length());

            rows.add(new InfraList.Row(
                    entry.getKey(),
                    entry.getValue().getAliasImplementation()
                            .stream()
                            .sorted()
                            .collect(Collectors.joining(", ")),
                    entry.getValue().getDescription()));
        }

        rows.sort(Comparator.comparing(InfraList.Row::alias));

        // Do something with the full services list (for example, filter)
        serviceConsumer.accept(rows);

        if (jsonOutput) {
            printer().println(
                    Jsoner.serialize(
                            rows.stream().map(row -> Map.of(
                                    "alias", row.alias(),
                                    "aliasImplementation", row.aliasImplementation(),
                                    "description", row.description() == null ? "" : row.description()))
                                    .collect(Collectors.toList())));
        } else {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("ALIAS").minWidth(width + 5).dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.alias()),
                    new Column().header("IMPLEMENTATION").maxWidth(40, OverflowBehaviour.NEWLINE)
                            .dataAlign(HorizontalAlign.LEFT).with(r -> r.aliasImplementation()),
                    new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).with(r -> r.description()))));
        }

        return 0;
    }

    public String getLogFileName(String service, String pid) {
        return String.format("infra-%s-%s.log", service, pid);
    }

    public String getJsonFileName(String service, String pid) {
        return String.format("infra-%s-%s.json", service, pid);
    }

    record TestInfraService(
            String service,
            String implementation,
            String description,
            List<String> alias,
            List<String> aliasImplementation,
            String groupId,
            String artifactId,
            String version) {
    }

    record Row(String alias, String aliasImplementation, String description) {
    }

    private class InfraServiceAlias {
        private final String description;
        private final Set<String> aliasImplementation = new HashSet<>();

        public InfraServiceAlias(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public Set<String> getAliasImplementation() {
            return aliasImplementation;
        }
    }
}
