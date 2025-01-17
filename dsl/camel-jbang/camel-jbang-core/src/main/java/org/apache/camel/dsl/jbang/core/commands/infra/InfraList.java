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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "Displays available external services", sortOptions = false,
                     showDefaultValues = true)
public class InfraList extends InfraBaseCommand {

    @CommandLine.Option(names = { "--running" },
                        description = "Display running services")
    boolean running;

    public InfraList(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Map<String, InfraServiceAlias> services = new LinkedHashMap<>();

        List<TestInfraService> metadata = getMetadata();

        List<Row> rows = new ArrayList<>(metadata.size());

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

            rows.add(new Row(
                    entry.getKey(),
                    entry.getValue().getAliasImplementation()
                            .stream()
                            .sorted()
                            .collect(Collectors.joining(", ")),
                    entry.getValue().getDescription()));
        }

        rows.sort(Comparator.comparing(Row::alias));

        if (running) {
            // Filter out non-running services from list
            List<String> runningAliases = new ArrayList<>();
            for (File pidFile : CommandLineHelper.getCamelDir().listFiles(
                    (dir, name) -> name.startsWith("infra-"))) {
                String runningServiceName = pidFile.getName().split("-")[1];
                runningAliases.add(runningServiceName);
            }

            rows.removeIf(row -> !runningAliases.contains(row.alias()));
        }

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
