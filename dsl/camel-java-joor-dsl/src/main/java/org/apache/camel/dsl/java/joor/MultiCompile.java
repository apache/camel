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
package org.apache.camel.dsl.java.joor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.joor.ReflectException;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

/**
 * Until jOOR supports multi-file compilation, then we have the compiler at Apache Camel. See:
 * https://github.com/jOOQ/jOOR/pull/119
 */
public final class MultiCompile {

    private MultiCompile() {
    }

    /**
     * Compiles multiple files as one unit
     *
     * @param  unit the files to compile in the same unit
     * @return      the compilation result
     */
    public static CompilationUnit.Result compileUnit(CompilationUnit unit) {
        CompilationUnit.Result result = CompilationUnit.result();

        // some classes may already be compiled so try to load them first
        List<CharSequenceJavaFileObject> files = new ArrayList<>();

        Lookup lookup = MethodHandles.lookup();
        ClassLoader cl = lookup.lookupClass().getClassLoader();
        unit.getInput().forEach((cn, code) -> {
            try {
                Class<?> clazz = cl.loadClass(cn);
                result.addResult(cn, clazz);
            } catch (ClassNotFoundException ignore) {
                files.add(new CharSequenceJavaFileObject(cn, code));
            }
        });

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        try {
            ClassFileManager fileManager = new ClassFileManager(compiler.getStandardFileManager(null, null, null));
            StringWriter out = new StringWriter();

            List<String> options = new ArrayList<>();
            if (!options.contains("-classpath")) {
                StringBuilder classpath = new StringBuilder();
                String separator = System.getProperty("path.separator");
                String cp = System.getProperty("java.class.path");
                String mp = System.getProperty("jdk.module.path");

                if (cp != null && !"".equals(cp)) {
                    classpath.append(cp);
                }
                if (mp != null && !"".equals(mp)) {
                    classpath.append(mp);
                }

                if (cl instanceof URLClassLoader) {
                    for (URL url : ((URLClassLoader) cl).getURLs()) {
                        if (classpath.length() > 0) {
                            classpath.append(separator);
                        }

                        if ("file".equals(url.getProtocol())) {
                            classpath.append(new File(url.toURI()));
                        }
                    }
                }

                options.addAll(Arrays.asList("-classpath", classpath.toString()));
            }

            CompilationTask task = compiler.getTask(out, fileManager, null, options, null, files);

            task.call();

            if (fileManager.isEmpty()) {
                throw new ReflectException("Compilation error: " + out);
            }

            // This method is called by client code from two levels up the current stack frame
            // We need a private-access lookup from the class in that stack frame in order to get
            // private-access to any local interfaces at that location.
            int index = 2;
            for (CharSequenceJavaFileObject f : files) {
                String className = f.getClassName();

                Class<?> caller = findCompiledClassViaIndex(index++);

                // If the compiled class is in the same package as the caller class, then
                // we can use the private-access Lookup of the caller class
                if (caller != null && className.startsWith(caller.getPackageName() + ".")
                        && Character.isUpperCase(className.charAt(caller.getPackageName().length() + 1))) {
                    // [#74] This heuristic is necessary to prevent classes in subpackages of the caller to be loaded
                    //       this way, as subpackages cannot access private content in super packages.
                    //       The heuristic will work only with classes that follow standard naming conventions.
                    //       A better implementation is difficult at this point.
                    Lookup privateLookup = MethodHandles.privateLookupIn(caller, lookup);
                    Class<?> clazz = fileManager.loadAndReturnMainClass(className,
                            (name, bytes) -> privateLookup.defineClass(bytes));
                    if (clazz != null) {
                        result.addResult(className, clazz);
                    }
                } else {
                    // Otherwise, use an arbitrary class loader. This approach doesn't allow for
                    // loading private-access interfaces in the compiled class's type hierarchy
                    ByteArrayClassLoader c = new ByteArrayClassLoader(fileManager.classes());
                    Class<?> clazz = fileManager.loadAndReturnMainClass(className,
                            (name, bytes) -> c.loadClass(name));
                    if (clazz != null) {
                        result.addResult(className, clazz);
                    }
                }
            }

            return result;
        } catch (ReflectException e) {
            throw e;
        } catch (Exception e) {
            throw new ReflectException("Error while compiling unit " + unit, e);
        }
    }

    private static Class<?> findCompiledClassViaIndex(int index) {
        StackWalker.StackFrame sf = StackWalker
                .getInstance(RETAIN_CLASS_REFERENCE)
                .walk(s -> s
                        .skip(index)
                        .findFirst()
                        .orElse(null));
        return sf != null ? sf.getDeclaringClass() : null;
    }

    /* [java-9] */
    static final class ByteArrayClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes;

        ByteArrayClassLoader(Map<String, byte[]> classes) {
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

    static final class JavaFileObject extends SimpleJavaFileObject {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();

        JavaFileObject(String name, Kind kind) {
            super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
        }

        byte[] getBytes() {
            return os.toByteArray();
        }

        @Override
        public OutputStream openOutputStream() {
            return os;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return os.toString(StandardCharsets.UTF_8);
        }
    }

    static final class ClassFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, JavaFileObject> fileObjectMap;
        private Map<String, byte[]> classes;

        ClassFileManager(StandardJavaFileManager standardManager) {
            super(standardManager);

            fileObjectMap = new LinkedHashMap<>();
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling) {
            JavaFileObject result = new JavaFileObject(className, kind);
            fileObjectMap.put(className, result);
            return result;
        }

        boolean isEmpty() {
            return fileObjectMap.isEmpty();
        }

        Map<String, byte[]> classes() {
            if (classes == null) {
                classes = new LinkedHashMap<>();

                for (Entry<String, JavaFileObject> entry : fileObjectMap.entrySet()) {
                    classes.put(entry.getKey(), entry.getValue().getBytes());
                }
            }

            return classes;
        }

        Class<?> loadAndReturnMainClass(String mainClassName, ThrowingBiFunction<String, byte[], Class<?>> definer)
                throws Exception {
            Class<?> result = null;

            // [#117] We don't know the subclass hierarchy of the top level
            //        classes in the compilation unit, and we can't find out
            //        without either:
            //
            //        - class loading them (which fails due to NoClassDefFoundError)
            //        - using a library like ASM (which is a big and painful dependency)
            //
            //        Simple workaround: try until it works, in O(n^2), where n
            //        can be reasonably expected to be small.
            Deque<Entry<String, byte[]>> queue = new ArrayDeque<>(classes().entrySet());
            int n1 = queue.size();

            // Try at most n times
            for (int i1 = 0; i1 < n1 && !queue.isEmpty(); i1++) {
                int n2 = queue.size();

                for (int i2 = 0; i2 < n2; i2++) {
                    Entry<String, byte[]> entry = queue.pop();

                    try {
                        Class<?> c = definer.apply(entry.getKey(), entry.getValue());

                        if (mainClassName.equals(entry.getKey())) {
                            result = c;
                        }
                    } catch (ReflectException e) {
                        queue.offer(entry);
                    }
                }
            }

            return result;
        }
    }

    @FunctionalInterface
    interface ThrowingBiFunction<T, U, R> {
        R apply(T t, U u) throws Exception;
    }

    static final class CharSequenceJavaFileObject extends SimpleJavaFileObject {
        final CharSequence content;
        final String className;

        public CharSequenceJavaFileObject(String className, CharSequence content) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.className = className;
            this.content = content;
        }

        public String getClassName() {
            return className;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

}
