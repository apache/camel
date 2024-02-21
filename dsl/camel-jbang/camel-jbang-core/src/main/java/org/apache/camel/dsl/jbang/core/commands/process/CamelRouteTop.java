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
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import picocli.CommandLine.Command;

@Command(name = "route", description = "Top performing routes",
         sortOptions = false)
public class CamelRouteTop extends CamelRouteStatus {

    public CamelRouteTop(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected void printTable(List<Row> rows) {
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("ID").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getId),
                new Column().header("FROM").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getFrom),
                new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> r.state),
                new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                new Column().header("LOAD").headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER)
                        .with(this::getLoad),
                new Column().header("TOTAL").with(r -> r.total),
                new Column().header("FAIL").with(r -> r.failed),
                new Column().header("INFLIGHT").with(r -> r.inflight),
                new Column().header("MEAN").with(r -> r.mean),
                new Column().header("MIN").with(r -> r.min),
                new Column().header("MAX").with(r -> r.max),
                new Column().header("SINCE-LAST").with(this::getSinceLast))));
    }

    private String getLoad(Row r) {
        String s1 = r.load01 != null ? r.load01 : "-";
        String s2 = r.load05 != null ? r.load05 : "-";
        String s3 = r.load15 != null ? r.load15 : "-";
        if ("0.00".equals(s1)) {
            s1 = "-";
        }
        if ("0.00".equals(s2)) {
            s2 = "-";
        }
        if ("0.00".equals(s3)) {
            s3 = "-";
        }
        if (s1.equals("-") && s2.equals("-") && s3.equals("-")) {
            return "0/0/0";
        }
        return s1 + "/" + s2 + "/" + s3;
    }

    @Override
    protected int sortRow(Row o1, Row o2) {
        // sort for highest mean value as we want the slowest in the top
        long m1 = o1.mean != null ? Long.parseLong(o1.mean) : 0;
        long m2 = o2.mean != null ? Long.parseLong(o2.mean) : 0;
        if (m1 < m2) {
            return 1;
        } else if (m1 > m2) {
            return -1;
        } else {
            return 0;
        }
    }

}
