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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.TimeUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "ps", description = "List running Camel integrations")
public class ListProcess extends ProcessBaseCommand {

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    public ListProcess(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        List<Row> rows = new ArrayList<>();

        final long cur = ProcessHandle.current().pid();
        ProcessHandle.allProcesses()
                .filter(ph -> ph.pid() != cur)
                .sorted((o1, o2) -> {
                    switch (sort) {
                        case "pid":
                            return Long.compare(o1.pid(), o2.pid());
                        case "name":
                            return extractName(o1).compareTo(extractName(o2));
                        case "age":
                            // we want newest in top
                            return Long.compare(extractSince(o1), extractSince(o2)) * -1;
                        default:
                            return 0;
                    }
                })
                .forEach(ph -> {
                    Row row = new Row();
                    row.name = extractName(ph);
                    if (ObjectHelper.isNotEmpty(row.name)) {
                        row.pid = "" + ph.pid();
                        row.age = TimeUtils.printSince(extractSince(ph));
                        rows.add(row);
                    }
                });

        if (!rows.isEmpty()) {
            System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS)
                            .with(r -> r.name),
                    new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age))));
        }

        return 0;
    }

    private static class Row {
        String pid;
        String name;
        String age;
    }

}
