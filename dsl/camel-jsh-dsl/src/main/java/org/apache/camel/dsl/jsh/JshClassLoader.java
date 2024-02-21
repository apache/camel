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
package org.apache.camel.dsl.jsh;

import java.util.HashMap;
import java.util.Map;

import jdk.jshell.spi.ExecutionControl;

/**
 * An implementation of a {@link ClassLoader} that allow hold class bytecode.
 */
final class JshClassLoader extends ClassLoader {
    private final Map<String, ExecutionControl.ClassBytecodes> types;

    JshClassLoader(ClassLoader parent) {
        super(parent);
        this.types = new HashMap<>();
    }

    void addClassBytecodes(ExecutionControl.ClassBytecodes classBytecodes) {
        types.put(toResourceString(classBytecodes.name()), classBytecodes);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        final String key = toResourceString(name);
        final ExecutionControl.ClassBytecodes cb = types.get(key);

        return cb == null
                ? super.findClass(name)
                : super.defineClass(name, cb.bytecodes(), 0, cb.bytecodes().length);
    }

    private static String toResourceString(String name) {
        return name.replace('.', '/') + ".class";
    }
}
