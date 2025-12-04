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

package org.apache.camel.language.groovy;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Classloader to load the pre-compiled Groovy sources.
 */
class GroovyPreCompiledClassLoader extends ClassLoader implements Closeable {

    private final Map<String, byte[]> classes = new HashMap<>();

    public GroovyPreCompiledClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public String getName() {
        return "GroovyPreCompiledClassLoader";
    }

    public void addClass(String name, byte[] data) {
        classes.put(name, data);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> answer;
        byte[] data = classes.get(name);
        if (data != null) {
            answer = defineClass(name, data, 0, data.length);
        } else {
            answer = super.findClass(name);
        }
        if (answer == null) {
            throw new ClassNotFoundException(name);
        }
        return answer;
    }

    @Override
    public void close() throws IOException {
        classes.clear();
    }
}
