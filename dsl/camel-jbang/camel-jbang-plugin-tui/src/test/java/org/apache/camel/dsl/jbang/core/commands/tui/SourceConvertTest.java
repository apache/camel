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

import java.util.List;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.dsl.jbang.core.commands.RouteDslConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Converting a route file to another DSL from the file actions of the Source tab (CAMEL-25254).
 */
class SourceConvertTest {

    /** The action chosen after moving down the menu the given number of times. */
    private static FileActionsPopup.Action choose(String file, boolean routeFile, int downs) {
        FileActionsPopup popup = new FileActionsPopup();
        popup.open(file, true, routeFile);
        for (int i = 0; i < downs; i++) {
            popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        FileActionsPopup.Request r = popup.consumeResult();
        return r != null ? r.action() : null;
    }

    @Test
    void aRouteFileConvertsToTheOtherTwoDsls() {
        // new file, new folder, rename, duplicate, delete, copy path, then the conversions
        assertThat(choose("orders.camel.xml", true, 6)).isEqualTo(FileActionsPopup.Action.CONVERT_YAML);
        assertThat(choose("orders.camel.xml", true, 7)).isEqualTo(FileActionsPopup.Action.CONVERT_JAVA);
        assertThat(choose("orders.camel.yaml", true, 6)).isEqualTo(FileActionsPopup.Action.CONVERT_XML);
        assertThat(choose("OrderRoute.java", true, 6)).isEqualTo(FileActionsPopup.Action.CONVERT_YAML);
        assertThat(choose("OrderRoute.java", true, 7)).isEqualTo(FileActionsPopup.Action.CONVERT_XML);
    }

    @Test
    void otherFilesHaveNoConversion() {
        // the menu ends at copy path: moving further stays on it
        assertThat(choose("pom.xml", false, 9)).isEqualTo(FileActionsPopup.Action.COPY_PATH);
    }

    @Test
    void theNotesGoAtTheTopOfTheConvertedFile() {
        List<String> notes = List.of("The comments of orders.camel.xml are not carried over");
        assertThat(RouteDslConverter.withNotes("- route:\n", notes, "yaml"))
                .startsWith("# The comments of orders.camel.xml are not carried over\n- route:");
        assertThat(RouteDslConverter.withNotes("<camel/>\n", notes, "xml"))
                .startsWith("<!--\n  The comments of orders.camel.xml are not carried over\n-->\n<camel/>");
        assertThat(RouteDslConverter.withNotes("import x;\n", notes, "java"))
                .startsWith("// The comments of orders.camel.xml are not carried over\nimport x;");
        assertThat(RouteDslConverter.withNotes("- route:\n", List.of(), "yaml")).isEqualTo("- route:\n");
    }
}
