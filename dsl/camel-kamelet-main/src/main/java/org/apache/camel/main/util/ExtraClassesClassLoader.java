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
package org.apache.camel.main.util;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.StringHelper;

/**
 * Classloader used to load the extra .class that were present in the CLI arguments
 */
public class ExtraClassesClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes = new HashMap<>();

    public ExtraClassesClassLoader(ClassLoader classLoader, List<String> names) {
        super(classLoader);

        for (String n : names) {
            addClass(new File(n));
        }
    }

    public void addClass(File file) {
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            String name = file.getPath();
            // file to java name
            name = name.replace('/', '.').replace('\\', '.');
            name = name.replace(".class", "");
            classes.put(name, data);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // find by FQN, then fallback to find by simple name
        byte[] bytes = classes.get(name);
        if (bytes == null) {
            String simple = StringHelper.afterLast(name, ".");
            if (simple != null) {
                bytes = classes.get(simple);
                // okay we now the real name, so move to FQN
                if (bytes != null) {
                    classes.put(name, bytes);
                    classes.remove(simple);
                }
            }
        }

        if (bytes == null) {
            return super.findClass(name);
        } else {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

}
