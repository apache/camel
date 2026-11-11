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
package org.apache.camel.dsl.jbang.core.commands;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.aesh.command.impl.internal.ProcessedOption;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The default and the completion candidates of every --sort option are keys of the order of its command, so that the
 * default does not fail with an unknown key.
 */
class SortOptionsTest extends CamelCommandBaseTestSupport {

    @Test
    void everySortValueIsAKeyOfTheOrder() throws Exception {
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);
        List<String> wrong = new ArrayList<>();
        List<String> checked = new ArrayList<>();
        check(main.createCommandLine(null), wrong, checked);

        assertThat(checked).as("commands with a --sort option and an order").hasSizeGreaterThan(40);
        assertThat(wrong).isEmpty();
    }

    private static void check(CommandTree commandLine, List<String> wrong, List<String> checked) throws Exception {
        for (CommandTree sub : new LinkedHashSet<>(commandLine.getSubcommands().values())) {
            ProcessedOption sort = sub.processedCommand().findLongOptionNoActivatorCheck("sort");
            RowSort<?> order = sort != null ? order(sub.getCommand().getClass()) : null;
            String name = sub.getQualifiedName();
            if (order != null) {
                checked.add(name);
                List<String> values = new ArrayList<>(sort.getDefaultValues());
                // camel cmd span sorts two views: its completion lists the keys of the traces, its order is the one of
                // the spans of --flat
                if (sort.completer() instanceof Iterable<?> candidates && !"camel cmd span".equals(name)) {
                    candidates.forEach(c -> values.add(String.valueOf(c)));
                }
                for (String value : values) {
                    try {
                        order.comparator(value);
                        order.comparator("-" + value);
                    } catch (CliException e) {
                        wrong.add(name + " --sort=" + value + ": " + e.getMessage());
                    }
                }
            }
            check(sub, wrong, checked);
        }
    }

    // the order of the command (a static field ORDER of the command or of a class it extends)
    private static RowSort<?> order(Class<?> type) throws IllegalAccessException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == RowSort.class) {
                    f.setAccessible(true);
                    return (RowSort<?>) f.get(null);
                }
            }
        }
        return null;
    }
}
