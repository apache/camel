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
package org.apache.camel.dsl.jbang.core.common;

import java.util.Collection;
import java.util.List;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The output of a command in JSON mode ({@code --json}): exactly one JSON document on stdout, so a script or an AI
 * agent can parse it.
 * <ul>
 * <li>A list prints {@code []} when it is empty.</li>
 * <li>An error prints {@code {"status":"error","code":3,"message":"..."}} with the exit code of the command, see
 * {@link CliExitCode}. The {@code ERROR:} line still goes to stderr.</li>
 * </ul>
 * Errors, warnings and tips go to stderr, so they never break the document.
 */
public final class JsonOutput {

    /**
     * The option that turns on JSON mode.
     */
    public static final String OPTION = "--json";

    private JsonOutput() {
    }

    /**
     * Prints a JSON document on stdout.
     *
     * @param printer  the printer of the command
     * @param document the document, such as a {@link JsonObject}, a map or a list
     */
    public static void print(Printer printer, Object document) {
        printer.println(Jsoner.serialize(document));
    }

    /**
     * Prints a list as a JSON array on stdout: {@code []} when it is empty or <tt>null</tt>.
     *
     * @param printer the printer of the command
     * @param rows    the rows, such as {@link JsonObject}s
     */
    public static void printList(Printer printer, Collection<?> rows) {
        print(printer, rows != null ? rows : List.of());
    }

    /**
     * The JSON document of an error.
     *
     * @param  code    the exit code of the command
     * @param  message what went wrong
     * @return         <tt>{"status":"error","code":code,"message":message}</tt>
     */
    public static JsonObject error(int code, String message) {
        JsonObject answer = new JsonObject();
        answer.put("status", "error");
        answer.put("code", code);
        answer.put("message", message);
        return answer;
    }

    /**
     * Prints the JSON document of an error on stdout.
     *
     * @param printer the printer of the command
     * @param code    the exit code of the command
     * @param message what went wrong
     */
    public static void printError(Printer printer, int code, String message) {
        print(printer, error(code, message));
    }

    /**
     * Whether the arguments of a command line turn on JSON mode. Only for errors found before the command runs, such as
     * an unknown option, as the command knows its own mode.
     *
     * @param args the arguments of the command line
     */
    public static boolean isRequested(String... args) {
        if (args != null) {
            for (String arg : args) {
                if ("--".equals(arg)) {
                    // the rest are not options of the command
                    return false;
                }
                if (OPTION.equals(arg) || (OPTION + "=true").equals(arg)) {
                    return true;
                }
            }
        }
        return false;
    }
}
