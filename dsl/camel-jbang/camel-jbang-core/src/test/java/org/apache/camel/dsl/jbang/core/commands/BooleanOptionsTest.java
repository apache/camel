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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.option.Mixin;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A boolean option given without a value is on, also when it is on by default (with picocli an option that was on by
 * default needed <tt>arity = "0..1", fallbackValue = "true"</tt>, else <tt>--maven-wrapper</tt> turned the Maven
 * wrapper off). With aesh a boolean option has no value (<tt>hasValue = false</tt>) or an optional one.
 */
class BooleanOptionsTest extends CamelCommandBaseTestSupport {

    @Test
    void everyBooleanOptionIsOnWhenGivenWithoutValue() throws Exception {
        List<String> wrong = new ArrayList<>();
        check(new CamelJBangMain().withPrinter(printer).createCommandLine(null), wrong);

        assertThat(wrong).as("boolean options that need a value").isEmpty();
    }

    @Test
    void flagWithoutValue() throws Exception {
        assertThat(option("maven-wrapper", "export", "--maven-wrapper")).isEqualTo(true);
        assertThat(option("maven-wrapper", "export", "--maven-wrapper=false")).isEqualTo(false);
        assertThat(option("maven-wrapper", "export")).isEqualTo(true);

        // the local configuration is written only with --global=false
        assertThat(option("global", "config", "set", "--global", "foo=bar")).isEqualTo(true);
        assertThat(option("global", "config", "set", "--global=false", "foo=bar")).isEqualTo(false);

        // the name after the flag is not taken as its value
        assertThat(option("timestamp", "log", "--timestamp", "orders")).isEqualTo(true);
        assertThat(field(parse("log", "--timestamp", "orders"), "name")).isEqualTo("orders");
    }

    private static void check(CommandTree commandLine, List<String> wrong) throws Exception {
        for (CommandTree sub : new LinkedHashSet<>(commandLine.getSubcommands().values())) {
            for (ProcessedOption option : sub.processedCommand().getOptions()) {
                boolean flag = option.type() == boolean.class || option.type() == Boolean.class;
                if (flag && option.hasValue() && !option.isOptionalValue()) {
                    wrong.add(sub.getQualifiedName() + " --" + option.name());
                }
            }
            check(sub, wrong);
        }
    }

    // the value of the option of the command, once the command line is parsed
    private Object option(String name, String... args) throws Exception {
        CommandTree tree = parse(args);
        String fieldName = tree.processedCommand().findLongOptionNoActivatorCheck(name).getFieldName();
        return field(tree, fieldName);
    }

    // the command that the command line names, with the values of the command line in its fields
    private CommandTree parse(String... args) throws Exception {
        CommandTree root = new CamelJBangMain().withPrinter(printer).createCommandLine(args[0]);
        root.parse(args);
        CommandTree tree = root;
        for (String arg : args) {
            CommandTree sub = tree.getSubcommands().get(arg);
            if (sub == null) {
                break;
            }
            tree = sub;
        }
        return tree;
    }

    private static Object field(CommandTree tree, String name) throws Exception {
        return field(tree.getCommand(), name);
    }

    // the field of the command, or of one of its mixins
    private static Object field(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getName().equals(name)) {
                    return f.get(target);
                }
                if (f.isAnnotationPresent(Mixin.class) && f.get(target) != null) {
                    Object value = field(f.get(target), name);
                    if (value != NOT_FOUND) {
                        return value;
                    }
                }
            }
        }
        return NOT_FOUND;
    }

    private static final Object NOT_FOUND = new Object();
}
