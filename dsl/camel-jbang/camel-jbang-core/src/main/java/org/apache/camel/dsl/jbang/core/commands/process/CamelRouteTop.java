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
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine.Command;

@Command(name = "route", description = "Top performing routes",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel top route",
                 "  camel top route --watch" })
public class CamelRouteTop extends CamelRouteStatus {

    public CamelRouteTop(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected void printTable(List<Row> rows, boolean remoteVisible) {
        if (jsonOutput) {
            super.printTable(rows, remoteVisible);
            return;
        }
        // Flexible column: FROM (40)
        // Fixed columns: PID(8)+NAME(30)+GROUP(20)+ID(25)+REMOTE(6)+STATUS(8)+AGE(8)+LOAD(12)+TOTAL(5)+FAIL(4)+INFLIGHT(8)+MEAN(4)+MIN(3)+MAX(3)+SINCE-LAST(10) ~= 154
        int tw = terminalWidth();
        int fromW = TerminalWidthHelper.flexWidth(tw, 154, TerminalWidthHelper.noBorderOverhead(16), 20, 40);
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("GROUP").visible(showGroup).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(20, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getGroup),
                new Column().header("ID").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getId),
                new Column().header("FROM").dataAlign(HorizontalAlign.LEFT).maxWidth(fromW, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getFrom),
                new Column().header("CHECKSUM").visible(checksum).dataAlign(HorizontalAlign.LEFT)
                        .with(r -> r.checksum != null ? r.checksum : ""),
                new Column().header("REV").visible(checksum).headerAlign(HorizontalAlign.CENTER)
                        .dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.revision > 0 ? String.valueOf(r.revision) : ""),
                new Column().header("REMOTE").visible(remoteVisible).headerAlign(HorizontalAlign.CENTER)
                        .dataAlign(HorizontalAlign.CENTER)
                        .with(this::getRemote),
                new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER)
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
