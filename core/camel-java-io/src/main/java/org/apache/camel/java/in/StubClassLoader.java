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
package org.apache.camel.java.in;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Stands in for the classes of the parsed project, which are never loaded: {@code onException(MyException.class)} needs
 * a {@link Class}, and the model only keeps its name. A stub is an empty abstract class with the same name that extends
 * or implements what the DSL method asks for. It has no code, so nothing of the project runs, and it lives in a class
 * loader of its own, for one parse.
 */
final class StubClassLoader extends ClassLoader {

    private static final Pattern JAVA_NAME
            = Pattern.compile("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*"
                              + "(\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*");

    /** At most this many stubs a parse. */
    static final int MAX_STUBS = 1000;

    private final Map<String, Class<?>> stubs = new HashMap<>();

    StubClassLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * An empty abstract class with the given name, extending {@code superType} when it is a class or implementing it
     * when it is an interface; null when the name cannot be used. It is only asked for a name that is not on the class
     * path, so a stub never hides a real class, a Camel one included.
     */
    Class<?> stub(String name, Class<?> superType) {
        if (!JAVA_NAME.matcher(name).matches() || name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("jdk.") || name.startsWith("sun.")) {
            return null;
        }
        Class<?> existing = stubs.get(name);
        if (existing != null) {
            return existing;
        }
        if (stubs.size() >= MAX_STUBS) {
            // each stub costs a little metaspace: a source naming thousands of classes gets names no more
            return null;
        }
        Class<?> base = superType == null ? Object.class : superType;
        boolean isInterface = base.isInterface();
        try {
            byte[] bytes = classFile(name, isInterface ? Object.class : base, isInterface ? base : null);
            Class<?> c = defineClass(name, bytes, 0, bytes.length);
            stubs.put(name, c);
            return c;
        } catch (IOException | LinkageError | SecurityException e) {
            return null;
        }
    }

    /** The bytes of {@code public abstract class name extends superClass [implements iface] {}}. */
    private static byte[] classFile(String name, Class<?> superClass, Class<?> iface) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0xCAFEBABE);
        out.writeShort(0);
        // Java 8 class files: no stack map frames needed, and there is no code
        out.writeShort(52);
        int count = iface != null ? 6 : 4;
        out.writeShort(count + 1);
        utf8(out, internal(name));
        classRef(out, 1);
        utf8(out, internal(superClass.getName()));
        classRef(out, 3);
        if (iface != null) {
            utf8(out, internal(iface.getName()));
            classRef(out, 5);
        }
        // public super abstract
        out.writeShort(0x0001 | 0x0020 | 0x0400);
        out.writeShort(2);
        out.writeShort(4);
        if (iface != null) {
            out.writeShort(1);
            out.writeShort(6);
        } else {
            out.writeShort(0);
        }
        // no fields, methods or attributes
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(0);
        out.flush();
        return bytes.toByteArray();
    }

    private static void utf8(DataOutputStream out, String s) throws IOException {
        out.writeByte(1);
        out.writeUTF(s);
    }

    private static void classRef(DataOutputStream out, int index) throws IOException {
        out.writeByte(7);
        out.writeShort(index);
    }

    private static String internal(String name) {
        return name.replace('.', '/');
    }
}
