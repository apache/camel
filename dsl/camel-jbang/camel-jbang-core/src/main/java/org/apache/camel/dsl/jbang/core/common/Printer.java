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

/**
 * Printer interface used by commands to write output to given print stream. By default, uses System out print stream,
 * but unit tests for instance may use a different print stream.
 */
public interface Printer {

    default void println() {
        System.out.println();
    }

    default void println(String line) {
        System.out.println(line);
    }

    default void print(String output) {
        System.out.print(output);
    }

    default void printf(String format, Object... args) {
        System.out.printf(format, args);
    }

    /**
     * Default printer uses System out print stream.
     */
    class SystemOutPrinter implements Printer {
    }
}
