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

import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.spi.ExecutionControl;

/**
 * A simple implementation of {@link LoaderDelegate} tailored for camel-k use case.
 */
final class JshLoaderDelegate implements LoaderDelegate {
    private final JshClassLoader loader;
    private final Map<String, Class<?>> types;

    public JshLoaderDelegate(JshClassLoader loader) {
        this.loader = loader;
        this.types = new HashMap<>();
    }

    @SuppressWarnings("PMD.PreserveStackTrace")
    @Override
    public void load(ExecutionControl.ClassBytecodes[] cbs)
            throws ExecutionControl.ClassInstallException, ExecutionControl.EngineTerminationException {

        boolean[] loaded = new boolean[cbs.length];
        try {
            for (ExecutionControl.ClassBytecodes cb : cbs) {
                loader.addClassBytecodes(cb);
            }
            for (int i = 0; i < cbs.length; ++i) {
                Class<?> type = loader.loadClass(cbs[i].name());
                type.getDeclaredMethods();

                types.put(cbs[i].name(), type);

                loaded[i] = true;
            }
        } catch (Exception ex) {
            throw new ExecutionControl.ClassInstallException("load: " + ex.getMessage(), loaded);
        }
    }

    @Override
    public void classesRedefined(ExecutionControl.ClassBytecodes[] cbcs) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void addToClasspath(String cp) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> type = types.get(name);
        if (type != null) {
            return type;
        }

        throw new ClassNotFoundException(name + " not found");
    }
}
