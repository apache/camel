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

    private CamelContext camelContext;

    private static final ClassLoader FALLBACK_CLASS_LOADER =
        ClassLoadingAwareObjectInputStream.class.getClassLoader();

    /**
     * Maps primitive type names to corresponding class objects.
     */
    private static final HashMap<String, Class> primClasses = new HashMap<String, Class>(8, 1.0F);

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
        final Class<?> clazz = (Class<?>) primClasses.get(className);
        if (clazz != null) {
            return clazz;
        } else {
            return Class.forName(className, false, FALLBACK_CLASS_LOADER);
        }
    }

    static {
        primClasses.put("boolean", boolean.class);
        primClasses.put("byte", byte.class);
        primClasses.put("char", char.class);
        primClasses.put("short", short.class);
        primClasses.put("int", int.class);
        primClasses.put("long", long.class);
        primClasses.put("float", float.class);
        primClasses.put("double", double.class);
        primClasses.put("void", void.class);
    }
}
