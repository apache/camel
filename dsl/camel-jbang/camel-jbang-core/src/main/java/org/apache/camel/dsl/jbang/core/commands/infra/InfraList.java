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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "Displays available external services", sortOptions = false,
                     showDefaultValues = true)
public class InfraList extends CamelCommand {

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    public InfraList(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Map<String, Set<String>> services = new HashMap<>();

        List<InfraCommand.TestInfraService> metadata;

        try (InputStream is
                = this.getClass().getClassLoader().getResourceAsStream("META-INF/test-infra-metadata.json")) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            metadata = InfraCommand.JSON_MAPPER.readValue(json, new TypeReference<List<InfraCommand.TestInfraService>>() {
            });
        }

        List<Row> rows = new ArrayList<>(metadata.size());

        for (InfraCommand.TestInfraService service : metadata) {
            for (String alias : service.alias()) {
                if (!services.containsKey(alias)) {
                    services.put(alias, new HashSet<>());
                }

                if (service.aliasImplementation() != null) {
                    for (String aliasImplementation : service.aliasImplementation()) {
                        services.get(alias).add(aliasImplementation);
                    }
                }
            }
        }

        int width = 0;
        for (Map.Entry<String, Set<String>> entry : services.entrySet()) {
            width = Math.max(width, entry.getKey().length());
            rows.add(new Row(entry.getKey(), String.join(", ", entry.getValue())));
        }

        if (jsonOutput) {
            printer().println(
                    Jsoner.serialize(
                            rows.stream().map(row -> Map.of(
                                    "alias", row.alias(),
                                    "aliasImplementation", row.aliasImplementation()))
                                    .collect(Collectors.toList())));
        } else {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("ALIAS").minWidth(width + 5).dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.alias()),
                    new Column().header("IMPLEMENTATION").dataAlign(HorizontalAlign.LEFT).with(r -> r.aliasImplementation()))));
        }

        return 0;
    }

    record Row(String alias, String aliasImplementation) {
    }

}
