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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.List;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A table whose list shrinks shows its rows from the top again: after 8 apps became 2, the first stayed above the top
 * of the Overview, and looked stopped.
 */
class TableOffsetClampTest {

    @Test
    void theOffsetIsPulledBackWhenRowsGoAway() {
        Rect area = new Rect(0, 0, 40, 6);
        TableState state = new TableState();
        state.setOffset(6);

        AbstractTab.clampTableOffset(table(2), area, state, 2);

        assertThat(state.offset()).isZero();
    }

    @Test
    void anOffsetThatStillFitsIsKept() {
        Rect area = new Rect(0, 0, 40, 6);
        Table table = table(20);
        int visible = table.viewportHeight(area);
        TableState state = new TableState();
        state.setOffset(5);

        AbstractTab.clampTableOffset(table, area, state, 20);
        assertThat(state.offset()).isEqualTo(5);

        state.setOffset(19);
        AbstractTab.clampTableOffset(table, area, state, 20);
        assertThat(state.offset()).isEqualTo(20 - visible);
    }

    private static Table table(int rows) {
        List<Row> list = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            list.add(Row.from("app-" + i));
        }
        return Table.builder().rows(list).header(Row.from("NAME")).widths(Constraint.fill())
                .block(Block.bordered()).build();
    }
}
