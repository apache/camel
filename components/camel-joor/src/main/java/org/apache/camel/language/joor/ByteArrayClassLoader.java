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
package org.apache.camel.language.joor;

import java.util.Map;

/**
 * {@link ClassLoader} that loads byte code from a byte array.
 */
final class ByteArrayClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes;

    public ByteArrayClassLoader(Map<String, byte[]> classes) {
        super(ByteArrayClassLoader.class.getClassLoader());
        this.classes = classes;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classes.get(name);

        if (bytes == null) {
            return super.findClass(name);
        } else {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
