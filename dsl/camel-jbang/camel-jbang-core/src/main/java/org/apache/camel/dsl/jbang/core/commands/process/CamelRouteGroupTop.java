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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ListingFormat;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine.Command;

@Command(name = "group", description = "Top performing route groups",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel top group",
                 "  camel top group --watch" })
public class CamelRouteGroupTop extends CamelRouteGroupStatus {

    public CamelRouteGroupTop(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected void printTable(List<Row> rows) {
        if (jsonOutput) {
            super.printTable(rows);
            return;
        }
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("GROUP").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(20, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getGroup),
                new Column().header("ROUTES").dataAlign(HorizontalAlign.RIGHT).headerAlign(HorizontalAlign.CENTER)
                        .with(r -> "" + r.size),
                new Column().header("STATUS").dataAlign(HorizontalAlign.LEFT).headerAlign(HorizontalAlign.CENTER)
                        .with(r -> r.state),
                new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                new Column().header("LOAD").headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER)
                        .with(this::getLoad),
                new Column().header("TOTAL").with(this::getTotal),
                new Column().header("FAIL").with(this::getFailed),
                new Column().header("INFLIGHT").with(this::getInflight),
                new Column().header("MEAN").with(r -> r.mean),
                new Column().header("MIN").with(r -> r.min),
                new Column().header("MAX").with(r -> r.max),
                new Column().header("SINCE-LAST").with(this::getSinceLast))));
    }

    @Override
    protected JsonObject toJson(Row r) {
        JsonObject jo = super.toJson(r);
        // the load averages of the last 1, 5 and 15 minutes
        jo.put("load01", JsonValues.decimal(r.load01));
        jo.put("load05", JsonValues.decimal(r.load05));
        jo.put("load15", JsonValues.decimal(r.load15));
        return jo;
    }

    private String getLoad(Row r) {
        return ListingFormat.load(r.load01, r.load05, r.load15);
    }

    @Override
    protected Comparator<Row> order() {
        // then the slowest first, as the top shows the slowest
        return super.order().thenComparing(ListingFormat.<Row> slowestFirst(sort, r -> r.mean));
    }

}
