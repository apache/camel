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
package org.apache.camel.dsl.java.joor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Until jOOR supports multi-file compilation, then we have the compiler at Apache Camel. See:
 * https://github.com/jOOQ/jOOR/pull/119
 */
public class CompilationUnit {

    private final Map<String, String> files = new LinkedHashMap<>();

    /**
     * The result of the compilation that holds mapping for each className -> class.
     */
    public static class Result {
        private final Map<String, Class<?>> classes = new LinkedHashMap<>();
        private final Map<String, byte[]> compiled = new LinkedHashMap<>();

        void addResult(String className, Class<?> clazz, byte[] byteCode) {
            if (clazz != null && !classes.containsKey(className)) {
                classes.put(className, clazz);
            }
            if (byteCode != null && !compiled.containsKey(className)) {
                compiled.put(className, byteCode);
            }
        }

        /**
         * Gets the compiled class by its class name
         *
         * @param  className the class name
         * @return           the compiled class
         */
        public Class<?> getClass(String className) {
            return classes.get(className);
        }

        /**
         * Gets the compiled byte code by its class name
         *
         * @param  className the class name
         * @return           the compiled byte code
         */
        public byte[] getByteCode(String className) {
            return compiled.get(className);
        }

        /**
         * Number of classes in the result
         */
        public int size() {
            return classes.size();
        }

        /**
         * Set of the classes by their names
         */
        public Set<String> getClassNames() {
            return classes.keySet();
        }

        /**
         * Set of the compiled classes by their names
         */
        public Set<String> getCompiledClassNames() {
            return compiled.keySet();
        }
    }

    static CompilationUnit.Result result() {
        return new Result();
    }

    /**
     * Creates a new compilation unit for holding input files.
     */
    public static CompilationUnit input() {
        return new CompilationUnit();
    }

    /**
     * Adds input to the compilation unit.
     *
     * @param className the class name
     * @param content   the source code for the class
     */
    public CompilationUnit addClass(String className, String content) {
        files.put(className, content);
        return this;
    }

    Map<String, String> getInput() {
        return files;
    }

}
