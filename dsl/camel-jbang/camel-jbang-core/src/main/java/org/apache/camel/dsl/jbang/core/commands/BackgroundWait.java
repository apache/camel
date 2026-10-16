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

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.apache.camel.util.TimeUtils;
import picocli.CommandLine;

/**
 * The <tt>--background-wait</tt> option of camel run: how long to wait for an integration that runs in the background
 * to start, in millis or as a duration such as <tt>2m</tt>. <tt>true</tt> (or no value) waits 60 seconds, and
 * <tt>false</tt> does not wait. A file name after the option is not its value.
 */
final class BackgroundWait {

    static final long DEFAULT = 60000;

    private BackgroundWait() {
    }

    /**
     * Whether the text is a value of the option, rather than a file name after it.
     */
    static boolean isValue(String text) {
        try {
            parse(text);
            return true;
        } catch (CommandLine.TypeConversionException e) {
            return false;
        }
    }

    /**
     * The time to wait, in millis (0 is not to wait).
     */
    static long parse(String text) {
        String value = text.trim();
        if ("true".equalsIgnoreCase(value)) {
            return DEFAULT;
        } else if ("false".equalsIgnoreCase(value)) {
            return 0;
        }
        try {
            long millis = TimeUtils.toMilliSeconds(value);
            if (millis >= 0) {
                return millis;
            }
        } catch (RuntimeException e) {
            // not a duration
        }
        throw new CommandLine.TypeConversionException(
                "'" + text + "' is not a time to wait: use true, false, millis or a duration such as 2m");
    }

    /**
     * The arguments of camel run for the integration that runs in the background: without <tt>--background</tt> and
     * <tt>--background-wait</tt> (with its value).
     */
    static List<String> withoutBackgroundOptions(List<String> args) {
        List<String> answer = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("--background-wait".equals(arg)) {
                if (i + 1 < args.size() && isValue(args.get(i + 1))) {
                    // its value
                    i++;
                }
            } else if (!"--background".equals(arg) && !arg.startsWith("--background=")
                    && !arg.startsWith("--background-wait=")) {
                answer.add(arg);
            }
        }
        return answer;
    }

    static class Converter implements CommandLine.ITypeConverter<Long> {
        @Override
        public Long convert(String value) {
            return parse(value);
        }
    }

    /**
     * Takes the next argument as the value only when it is one, so <tt>--background-wait hello.yaml</tt> runs
     * hello.yaml, as it did when the option was a boolean.
     */
    static class Consumer implements CommandLine.IParameterConsumer {
        @Override
        public void consumeParameters(
                Stack<String> args, CommandLine.Model.ArgSpec argSpec, CommandLine.Model.CommandSpec commandSpec) {
            String value = "true";
            if (!args.isEmpty() && isValue(args.peek())) {
                value = args.pop();
            }
            argSpec.setValue(parse(value));
        }
    }
}
