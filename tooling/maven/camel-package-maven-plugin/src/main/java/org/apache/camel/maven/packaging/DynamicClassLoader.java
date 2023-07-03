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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class DynamicClassLoader extends URLClassLoader {

    public DynamicClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public static DynamicClassLoader createDynamicClassLoaderFromUrls(List<URL> classpathElements) {
        final URL[] urls = new URL[classpathElements.size()];
        int i = 0;
        for (Iterator<URL> it = classpathElements.iterator(); it.hasNext(); i++) {
            urls[i] = it.next();
        }
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return new DynamicClassLoader(urls, tccl != null ? tccl : DynamicClassLoader.class.getClassLoader());
    }

    public static DynamicClassLoader createDynamicClassLoader(List<String> classpathElements) {
        final URL[] urls = new URL[classpathElements.size()];
        int i = 0;
        for (Iterator<?> it = classpathElements.iterator(); it.hasNext(); i++) {
            try {
                urls[i] = new File((String) it.next()).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return new DynamicClassLoader(urls, tccl != null ? tccl : DynamicClassLoader.class.getClassLoader());
    }

    public Class<?> defineClass(String name, byte[] data) {
        return super.defineClass(name, data, 0, data.length);
    }

    public Class<?> generateDummyClass(String clazzName) {
        try {
            return loadClass(clazzName);
        } catch (ClassNotFoundException e) {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, clazzName.replace('.', '/'), null, "java/lang/Object", null);
            cw.visitEnd();
            return defineClass(clazzName, cw.toByteArray());
        }
    }
}
