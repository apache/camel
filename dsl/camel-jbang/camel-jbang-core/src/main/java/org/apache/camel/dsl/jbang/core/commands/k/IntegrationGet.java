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
package org.apache.camel.dsl.jbang.core.commands.k;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.v1.Integration;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "get", description = "List Camel integrations deployed on Kubernetes", sortOptions = false)
public class IntegrationGet extends KubeBaseCommand {

    @CommandLine.Option(names = { "--name" },
                        description = "List only given integration name in the output")
    boolean name;

    public IntegrationGet(CamelJBangMain main) {
        super(main);
    }

    public Integer doCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Integration> integrations = client(Integration.class).list().getItems();
        integrations
                .forEach(integration -> {
                    Row row = new Row();
                    row.name = integration.getMetadata().getName();

                    row.ready = "0/1";
                    if (integration.getStatus() != null) {
                        row.phase = integration.getStatus().getPhase();

                        if (integration.getStatus().getConditions() != null) {
                            row.ready
                                    = integration.getStatus().getConditions().stream().filter(c -> c.getType().equals("Ready"))
                                            .anyMatch(c -> c.getStatus().equals("True")) ? "1/1" : "0/1";
                        }

                        row.kit = integration.getStatus().getIntegrationKit() != null
                                ? integration.getStatus().getIntegrationKit().getName() : "";
                    } else {
                        row.phase = "Unknown";
                    }

                    rows.add(row);
                });

        if (!rows.isEmpty()) {
            if (name) {
                rows.forEach(r -> printer().println(r.name));
            } else {
                printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                        new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.name),
                        new Column().header("PHASE").headerAlign(HorizontalAlign.LEFT)
                                .with(r -> r.phase),
                        new Column().header("KIT").headerAlign(HorizontalAlign.LEFT).with(r -> r.kit),
                        new Column().header("READY").dataAlign(HorizontalAlign.CENTER).with(r -> r.ready))));
            }
        }

        return 0;
    }

    private static class Row {
        String name;
        String ready;
        String phase;
        String kit;
    }

}
