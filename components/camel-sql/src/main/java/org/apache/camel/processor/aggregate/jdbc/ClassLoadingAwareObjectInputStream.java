/**
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
package org.apache.camel.processor.aggregate.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;
import java.util.HashMap;

import org.apache.camel.CamelContext;

/**
 * This class is copied from the Apache ActiveMQ project.
 */
@SuppressWarnings("rawtypes")
public class ClassLoadingAwareObjectInputStream extends ObjectInputStream {

    private static final ClassLoader FALLBACK_CLASS_LOADER = ClassLoadingAwareObjectInputStream.class.getClassLoader();

    /**
     * Maps primitive type names to corresponding class objects.
     */
    private static final HashMap<String, Class> PRIM_CLASSES = new HashMap<String, Class>(8, 1.0F);

    private CamelContext camelContext;
    private final ClassLoader inLoader;

    public ClassLoadingAwareObjectInputStream(InputStream in) throws IOException {
        super(in);
        inLoader = in.getClass().getClassLoader();
    }

    public ClassLoadingAwareObjectInputStream(CamelContext camelContext, InputStream in) throws IOException {
        super(in);
        inLoader = camelContext.getApplicationContextClassLoader();
    }

    protected Class<?> resolveClass(ObjectStreamClass classDesc) throws IOException, ClassNotFoundException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return load(classDesc.getName(), cl, inLoader);
    }

    protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class[] cinterfaces = new Class[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            cinterfaces[i] = load(interfaces[i], cl);
        }

        try {
            return Proxy.getProxyClass(cl, cinterfaces);
        } catch (IllegalArgumentException e) {
            try {
                return Proxy.getProxyClass(inLoader, cinterfaces);
            } catch (IllegalArgumentException e1) {
                // ignore
            }
            try {
                return Proxy.getProxyClass(FALLBACK_CLASS_LOADER, cinterfaces);
            } catch (IllegalArgumentException e2) {
                // ignore
            }

            throw new ClassNotFoundException(null, e);
        }
    }

    private Class<?> load(String className, ClassLoader... cl) throws ClassNotFoundException {
        for (ClassLoader loader : cl) {
            try {
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        // fallback
        final Class<?> clazz = PRIM_CLASSES.get(className);
        if (clazz != null) {
            return clazz;
        } else {
            return Class.forName(className, false, FALLBACK_CLASS_LOADER);
        }
    }

    static {
        PRIM_CLASSES.put("boolean", boolean.class);
        PRIM_CLASSES.put("byte", byte.class);
        PRIM_CLASSES.put("char", char.class);
        PRIM_CLASSES.put("short", short.class);
        PRIM_CLASSES.put("int", int.class);
        PRIM_CLASSES.put("long", long.class);
        PRIM_CLASSES.put("float", float.class);
        PRIM_CLASSES.put("double", double.class);
        PRIM_CLASSES.put("void", void.class);
    }
}
