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

import org.codehaus.groovy.runtime.InvokerHelper;

public class GroovyScriptClassLoader extends ClassLoader implements Closeable {

    private final Map<String, Class<?>> classes = new HashMap<>();

    public GroovyScriptClassLoader(ClassLoader parent) {
        super(parent);
    }

    public void addClass(String name, Class<?> clazz) {
        classes.put(name, clazz);
    }

    /**
     * Number of classes in this classloader
     */
    public int size() {
        return classes.size();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> answer = classes.get(name);
        if (answer == null) {
            answer = super.findClass(name);
        }
        return answer;
    }

    @Override
    public void close() throws IOException {
        for (Class<?> clazz : classes.values()) {
            InvokerHelper.removeClass(clazz);
        }
        classes.clear();
    }

}
