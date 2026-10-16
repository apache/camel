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
package org.apache.camel.test.dst;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

/**
 * Byte code level support for the code under test, which the harness does not otherwise control:
 * <ul>
 * <li>rewrites {@code System.currentTimeMillis()} and {@code System.nanoTime()} in the classes of the given packages to
 * {@link DstClock}, so the code under test reads virtual time (no change of the component's source);</li>
 * <li>reports every other escape point it finds in those classes: own threads and executors, sleeps, wall clock reads
 * through {@code java.time}, the common fork-join pool, Reactor schedulers, blocking waits.</li>
 * </ul>
 * Uses a runtime-attached agent (as Mockito's inline mock maker does) and the ASM copy shipped in Byte Buddy.
 */
public final class DstInstrumentation {

    private static final String CLOCK = "org/apache/camel/test/dst/DstClock";
    private static final Set<String> PREFIXES = new CopyOnWriteArraySet<>();
    private static Instrumentation instrumentation;

    private DstInstrumentation() {
    }

    /** Escape point categories. */
    public static final String VIRTUALIZED = "time (virtualized)";
    public static final String WALL_CLOCK = "wall clock (not virtualized)";
    public static final String SLEEP = "sleep";
    public static final String OWN_THREAD = "own thread";
    public static final String OWN_EXECUTOR = "own executor/timer";
    public static final String DEFAULT_POOL = "default async pool";
    public static final String REACTOR = "reactor scheduler";
    public static final String BLOCKING = "blocking wait";

    /**
     * Installs the time rewriting for the classes of these packages (and their sub packages). Classes already loaded
     * are retransformed.
     */
    public static synchronized void install(String... packages) {
        List<String> added = new ArrayList<>();
        for (String p : packages) {
            String internal = p.replace('.', '/') + "/";
            if (PREFIXES.add(internal)) {
                added.add(internal);
            }
        }
        if (added.isEmpty()) {
            return;
        }
        if (instrumentation == null) {
            instrumentation = ByteBuddyAgent.install();
            instrumentation.addTransformer(new TimeTransformer(), true);
        }
        List<Class<?>> loaded = new ArrayList<>();
        for (Class<?> c : instrumentation.getAllLoadedClasses()) {
            String name = c.getName().replace('.', '/');
            if (instrumentation.isModifiableClass(c) && added.stream().anyMatch(name::startsWith)) {
                loaded.add(c);
            }
        }
        if (!loaded.isEmpty()) {
            try {
                instrumentation.retransformClasses(loaded.toArray(new Class<?>[0]));
            } catch (UnmodifiableClassException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static boolean matches(String internalName) {
        for (String p : PREFIXES) {
            if (internalName.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private static final class TimeTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(
                Module module, ClassLoader loader, String className, Class<?> redefined, ProtectionDomain domain,
                byte[] bytes) {
            if (className == null || !matches(className)) {
                return null;
            }
            try {
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, 0);
                reader.accept(new EscapeVisitor(writer, true, null), 0);
                return writer.toByteArray();
            } catch (RuntimeException e) {
                DstEscapes.recordRuntime("could not rewrite " + className + ": " + e);
                return null;
            }
        }
    }

    /**
     * Scans every class of the package (and sub packages) found on the class path, loaded or not, for escape points.
     * Sorted, one line per call site: {@code category | Class.method:line | call}.
     */
    public static List<String> scan(String packageName) {
        String path = packageName.replace('.', '/');
        Set<String> found = new TreeSet<>();
        try {
            Enumeration<URL> roots = Thread.currentThread().getContextClassLoader().getResources(path);
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                if ("file".equals(url.getProtocol())) {
                    Path dir = Paths.get(url.toURI());
                    try (Stream<Path> files = Files.walk(dir)) {
                        for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                            try (InputStream in = Files.newInputStream(f)) {
                                scanClass(in, found);
                            }
                        }
                    }
                } else if ("jar".equals(url.getProtocol())) {
                    JarURLConnection conn = (JarURLConnection) url.openConnection();
                    conn.setUseCaches(false);
                    try (JarFile jar = conn.getJarFile()) {
                        for (JarEntry entry : Collections.list(jar.entries())) {
                            if (entry.getName().startsWith(path + "/") && entry.getName().endsWith(".class")) {
                                try (InputStream in = jar.getInputStream(entry)) {
                                    scanClass(in, found);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            found.add("scan failed: " + e);
        }
        return new ArrayList<>(found);
    }

    private static void scanClass(InputStream in, Set<String> found) throws IOException {
        ClassReader reader = new ClassReader(in);
        reader.accept(new EscapeVisitor(null, false, found), 0);
    }

    private static final class EscapeVisitor extends ClassVisitor {
        private final boolean rewrite;
        private final Set<String> sink;
        private String className;
        private String sourceFile;

        EscapeVisitor(ClassVisitor next, boolean rewrite, Set<String> sink) {
            super(Opcodes.ASM9, next);
            this.rewrite = rewrite;
            this.sink = sink;
        }

        @Override
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitSource(String source, String debug) {
            this.sourceFile = source;
            super.visitSource(source, debug);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = cv != null ? super.visitMethod(access, name, descriptor, signature, exceptions) : null;
            return new EscapeMethodVisitor(mv, name);
        }

        private final class EscapeMethodVisitor extends MethodVisitor {
            private final String method;
            private int line;

            EscapeMethodVisitor(MethodVisitor mv, String method) {
                super(Opcodes.ASM9, mv);
                this.method = method;
            }

            @Override
            public void visitLineNumber(int line, Label start) {
                this.line = line;
                super.visitLineNumber(line, start);
            }

            private void report(String category, String call) {
                String simple = className.substring(className.lastIndexOf('/') + 1);
                String where = simple + "." + method + ":" + line;
                String entry = String.format("%-28s | %-60s | %s", category, where, call);
                if (sink != null) {
                    sink.add(entry);
                } else {
                    DstEscapes.recordLoaded(entry);
                }
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                if (opcode == Opcodes.NEW) {
                    switch (type) {
                        case "java/lang/Thread" -> report(OWN_THREAD, "new Thread");
                        case "java/util/Timer" -> report(OWN_EXECUTOR, "new Timer");
                        case "java/util/concurrent/ThreadPoolExecutor", "java/util/concurrent/ScheduledThreadPoolExecutor",
                                "java/util/concurrent/ForkJoinPool" ->
                            report(OWN_EXECUTOR, "new " + type.substring(type.lastIndexOf('/') + 1));
                        default -> {
                            // not an escape point
                        }
                    }
                }
                super.visitTypeInsn(opcode, type);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                String call = owner.substring(owner.lastIndexOf('/') + 1) + "." + name;
                if ("java/lang/System".equals(owner) && ("currentTimeMillis".equals(name) || "nanoTime".equals(name))) {
                    report(VIRTUALIZED, call);
                    if (rewrite) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CLOCK, name, descriptor, false);
                        return;
                    }
                } else if ("java/lang/Thread".equals(owner)) {
                    if ("sleep".equals(name)) {
                        report(SLEEP, call);
                    } else if (name.startsWith("ofVirtual") || name.startsWith("ofPlatform")
                            || "startVirtualThread".equals(name)) {
                        report(OWN_THREAD, call);
                    }
                } else if ("java/util/concurrent/TimeUnit".equals(owner) && "sleep".equals(name)) {
                    report(SLEEP, call);
                } else if ("java/util/concurrent/Executors".equals(owner) && name.startsWith("new")) {
                    report(OWN_EXECUTOR, call);
                } else if ("java/util/concurrent/ForkJoinPool".equals(owner) && "commonPool".equals(name)) {
                    report(DEFAULT_POOL, call);
                } else if ("java/util/concurrent/CompletableFuture".equals(owner)) {
                    if (name.endsWith("Async") && !descriptor.contains("Ljava/util/concurrent/Executor;")) {
                        report(DEFAULT_POOL, call);
                    } else if ("delayedExecutor".equals(name)) {
                        report(DEFAULT_POOL, call);
                    } else if ("get".equals(name) || "join".equals(name)) {
                        report(BLOCKING, call);
                    }
                } else if (owner.startsWith("java/time/") && "now".equals(name)) {
                    report(WALL_CLOCK, call);
                } else if ("java/time/Clock".equals(owner) && name.startsWith("system")) {
                    report(WALL_CLOCK, call);
                } else if ("java/util/Date".equals(owner) && "<init>".equals(name) && "()V".equals(descriptor)) {
                    report(WALL_CLOCK, "new Date()");
                } else if ("reactor/core/scheduler/Schedulers".equals(owner)) {
                    report(REACTOR, call);
                } else if (("reactor/core/publisher/Mono".equals(owner) || "reactor/core/publisher/Flux".equals(owner))
                        && (name.startsWith("delay") || "interval".equals(name) || "timeout".equals(name)
                                || "publishOn".equals(name) || "subscribeOn".equals(name))) {
                    report(REACTOR, call);
                } else if (("reactor/core/publisher/Mono".equals(owner) || "reactor/core/publisher/Flux".equals(owner))
                        && name.startsWith("block")) {
                    report(BLOCKING, call);
                } else if ("java/lang/Object".equals(owner) && "wait".equals(name)
                        || "java/util/concurrent/locks/LockSupport".equals(owner) && name.startsWith("park")
                        || "java/util/concurrent/CountDownLatch".equals(owner) && "await".equals(name)
                        || "java/util/concurrent/Future".equals(owner) && "get".equals(name)) {
                    report(BLOCKING, call);
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, itf);
            }
        }
    }

    /** The packages whose time calls are rewritten. */
    public static Set<String> packages() {
        Set<String> answer = new LinkedHashSet<>();
        PREFIXES.forEach(p -> answer.add(p.replace('/', '.').substring(0, p.length() - 1)));
        return answer;
    }
}
