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
 * <li>rewrites the time reads in the classes of the given packages to {@link DstClock}, so the code under test reads
 * virtual time (no change of the component's source): {@code System.currentTimeMillis()}, {@code System.nanoTime()},
 * {@code new Date()}, {@code Instant.now()}, {@code Clock.systemUTC()}, {@code Clock.systemDefaultZone()},
 * {@code Clock.system(ZoneId)} and {@code now()} / {@code now(ZoneId)} of the {@code java.time} date and time
 * types;</li>
 * <li>rewrites the sleeps ({@code Thread.sleep}, {@code TimeUnit.sleep}) to virtual waits of {@link DstClock}: on the
 * loop thread the sleeping task keeps its thread while the other tasks run, and no real time passes;</li>
 * <li>rewrites the blocking waits ({@code CompletableFuture.get/join}, {@code Future.get},
 * {@code CountDownLatch.await}, {@code Semaphore.acquire}, {@code DelayQueue} and {@code BlockingQueue}
 * {@code take/poll}) to {@link DstWaits}: on the loop thread they run the loop until the wait can end, or fail the seed
 * when nothing can end it;</li>
 * <li>rewrites {@code parallelStream()} of the {@code java.util} collections to {@code stream()}: the elements are
 * handled in order by the calling task instead of on the common fork-join pool;</li>
 * <li>reports every other escape point it finds in those classes: own threads and executors, wall clock reads that are
 * not rewritten, the common fork-join pool, Reactor schedulers, blocking waits it cannot run the loop for; and adds a
 * call to {@link DstEscapes#reached(String)} before each, so that in strict mode reaching one at runtime fails the
 * seed.</li>
 * </ul>
 * {@link #alsoRewrite(String...)} applies the same rewriting, without the report, to classes outside the code under
 * test, for example {@code org.apache.camel.support.MonotonicClock} and {@code org.apache.camel.util.StopWatch} of
 * Camel core. That is a stop-gap until Camel core has a time source the harness can replace.
 * <p>
 * Uses a runtime-attached agent (as Mockito's inline mock maker does) and the ASM copy shipped in Byte Buddy.
 */
public final class DstInstrumentation {

    private static final String CLOCK = "org/apache/camel/test/dst/DstClock";
    private static final String WAITS = "org/apache/camel/test/dst/DstWaits";
    private static final String ESCAPES = "org/apache/camel/test/dst/DstEscapes";
    private static final String TIME_UNIT = "Ljava/util/concurrent/TimeUnit;";
    private static final Set<String> FUTURES = Set.of(
            "java/util/concurrent/Future", "java/util/concurrent/ScheduledFuture", "java/util/concurrent/RunnableFuture",
            "java/util/concurrent/RunnableScheduledFuture", "java/util/concurrent/FutureTask");
    private static final Set<String> QUEUES = Set.of(
            "java/util/concurrent/BlockingQueue", "java/util/concurrent/BlockingDeque",
            "java/util/concurrent/TransferQueue", "java/util/concurrent/LinkedBlockingQueue",
            "java/util/concurrent/ArrayBlockingQueue", "java/util/concurrent/LinkedBlockingDeque",
            "java/util/concurrent/PriorityBlockingQueue", "java/util/concurrent/SynchronousQueue",
            "java/util/concurrent/LinkedTransferQueue");
    private static final Set<String> PREFIXES = new CopyOnWriteArraySet<>();
    private static final Set<String> ALSO = new CopyOnWriteArraySet<>();
    private static final Set<String> WORLD = new CopyOnWriteArraySet<>();
    private static Instrumentation instrumentation;

    private DstInstrumentation() {
    }

    /** Escape point categories. */
    public static final String VIRTUALIZED = "time (virtualized)";
    public static final String WALL_CLOCK = "wall clock (not virtualized)";
    public static final String SLEEP = "sleep (virtual)";
    public static final String OWN_THREAD = "own thread";
    public static final String OWN_EXECUTOR = "own executor/timer";
    public static final String DEFAULT_POOL = "default async pool";
    public static final String REACTOR = "reactor scheduler";
    public static final String BLOCKING = "blocking wait";
    public static final String BLOCKING_LOOP = "blocking wait (runs the loop)";
    public static final String SEQUENTIAL = "parallel stream (sequential)";

    /**
     * Installs the time rewriting for the classes of these packages (and their sub packages): the code under test.
     * Classes already loaded are retransformed.
     */
    public static synchronized void install(String... packages) {
        List<String> added = new ArrayList<>();
        for (String p : packages) {
            String internal = p.replace('.', '/') + "/";
            if (PREFIXES.add(internal)) {
                added.add(internal);
            }
        }
        retransform(added);
    }

    /**
     * The package of a test and its world (the fakes): never rewritten nor scanned, also when it lies inside a package
     * of the code under test (a world in the tests of the component, such as
     * {@code org.apache.camel.component.foo.dst}). Only this package, not its sub packages.
     */
    public static synchronized void world(String packageName) {
        WORLD.add(packageName.replace('.', '/') + "/");
    }

    private static boolean inWorld(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash > 0 && WORLD.contains(internalName.substring(0, slash + 1));
    }

    /**
     * Rewrites the time reads of these classes or packages too, without reporting their escape points: a stop-gap for
     * code outside the code under test that reads the clock, such as {@code org.apache.camel.support.MonotonicClock} or
     * {@code org.apache.camel.util.StopWatch}. An entry whose last segment starts with an upper case letter is a class
     * (with its nested classes), else a package (with its sub packages). The rewriting stays for the rest of the JVM;
     * outside a run the rewritten calls read the real clock.
     */
    public static synchronized void alsoRewrite(String... classesOrPackages) {
        List<String> added = new ArrayList<>();
        for (String name : classesOrPackages) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String internal = name.trim().replace('.', '/');
            String last = internal.substring(internal.lastIndexOf('/') + 1);
            if (last.isEmpty() || !Character.isUpperCase(last.charAt(0))) {
                internal = internal + "/";
            }
            if (ALSO.add(internal)) {
                added.add(internal);
            }
        }
        retransform(added);
    }

    private static void retransform(List<String> added) {
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
            if (instrumentation.isModifiableClass(c) && !inWorld(name) && added.stream().anyMatch(p -> matches(p, name))) {
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

    private static boolean matches(String prefix, String internalName) {
        if (prefix.endsWith("/")) {
            return internalName.startsWith(prefix);
        }
        return internalName.equals(prefix) || internalName.startsWith(prefix + "$");
    }

    private static boolean underTest(String internalName) {
        for (String p : PREFIXES) {
            if (matches(p, internalName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean alsoRewritten(String internalName) {
        for (String p : ALSO) {
            if (matches(p, internalName)) {
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
            if (className == null) {
                return null;
            }
            if (inWorld(className)) {
                return null;
            }
            boolean report = underTest(className);
            if (!report && !alsoRewritten(className)) {
                return null;
            }
            try {
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, 0);
                reader.accept(new EscapeVisitor(writer, true, report, null), 0);
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
            List<URL> roots = Collections.list(Thread.currentThread().getContextClassLoader().getResources(path));
            // the code under test is the main code: test classes in the same package are left out, unless the package
            // has test classes only (the samples of the harness's own tests)
            boolean main = roots.stream().anyMatch(u -> !isTestClasses(u));
            for (URL url : roots) {
                if (main && isTestClasses(url)) {
                    continue;
                }
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

    private static boolean isTestClasses(URL url) {
        return url.getPath().contains("/test-classes/");
    }

    private static void scanClass(InputStream in, Set<String> found) throws IOException {
        ClassReader reader = new ClassReader(in);
        if (inWorld(reader.getClassName())) {
            return;
        }
        reader.accept(new EscapeVisitor(null, false, true, found), 0);
    }

    private static final class EscapeVisitor extends ClassVisitor {
        private final boolean rewrite;
        private final boolean reportEscapes;
        private final Set<String> sink;
        private String className;
        private String sourceFile;

        EscapeVisitor(ClassVisitor next, boolean rewrite, boolean reportEscapes, Set<String> sink) {
            super(Opcodes.ASM9, next);
            this.rewrite = rewrite;
            this.reportEscapes = reportEscapes;
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

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                // room for the values the rewritten calls push (new Date(): the long time stamp)
                super.visitMaxs(rewrite ? maxStack + 3 : maxStack, maxLocals);
            }

            private String where() {
                return className.substring(className.lastIndexOf('/') + 1) + "." + method + ":" + line;
            }

            private String report(String category, String call) {
                if (!reportEscapes) {
                    return null;
                }
                String where = where();
                String entry = String.format("%-28s | %-60s | %s", category, where, call);
                if (sink != null) {
                    sink.add(entry);
                } else {
                    DstEscapes.recordLoaded(entry);
                }
                return entry;
            }

            /** An escape point the harness does not control: reported, and checked at runtime (strict mode). */
            private void escape(String category, String call) {
                String entry = report(category, call);
                if (entry != null && rewrite) {
                    super.visitLdcInsn(entry);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, ESCAPES, "reached", "(Ljava/lang/String;)V", false);
                }
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                if (opcode == Opcodes.NEW) {
                    switch (type) {
                        case "java/lang/Thread" -> escape(OWN_THREAD, "new Thread");
                        case "java/util/Timer" -> escape(OWN_EXECUTOR, "new Timer");
                        case "java/util/concurrent/ThreadPoolExecutor", "java/util/concurrent/ScheduledThreadPoolExecutor",
                                "java/util/concurrent/ForkJoinPool" ->
                            escape(OWN_EXECUTOR, "new " + type.substring(type.lastIndexOf('/') + 1));
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
                String[] wait = waitTarget(owner, name, descriptor);
                if (wait != null) {
                    report(BLOCKING_LOOP, call);
                    if (rewrite) {
                        super.visitLdcInsn(where());
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WAITS, wait[0], wait[1], false);
                    } else {
                        super.visitMethodInsn(opcode, owner, name, descriptor, itf);
                    }
                    return;
                }
                if ("java/lang/System".equals(owner) && ("currentTimeMillis".equals(name) || "nanoTime".equals(name))) {
                    report(VIRTUALIZED, call);
                    if (rewrite) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CLOCK, name, descriptor, false);
                        return;
                    }
                } else if (owner.startsWith("java/time/") && "now".equals(name) && clockTarget(owner, descriptor) != null) {
                    report(VIRTUALIZED, call);
                    if (rewrite) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CLOCK, clockTarget(owner, descriptor), descriptor, false);
                        return;
                    }
                } else if ("java/time/Clock".equals(owner) && ("systemUTC".equals(name) || "systemDefaultZone".equals(name)
                        || "system".equals(name) && "(Ljava/time/ZoneId;)Ljava/time/Clock;".equals(descriptor))) {
                    report(VIRTUALIZED, call);
                    if (rewrite) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CLOCK, name, descriptor, false);
                        return;
                    }
                } else if ("java/util/Date".equals(owner) && "<init>".equals(name) && "()V".equals(descriptor)) {
                    report(VIRTUALIZED, "new Date()");
                    if (rewrite) {
                        // new Date() -> new Date(DstClock.currentTimeMillis())
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CLOCK, "currentTimeMillis", "()J", false);
                        super.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, name, "(J)V", false);
                        return;
                    }
                } else if ("java/lang/Thread".equals(owner) && "sleep".equals(name)) {
                    report(SLEEP, call);
                    if (rewrite) {
                        // the call site goes along, for the trace
                        super.visitLdcInsn(where());
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CLOCK, "sleep",
                                descriptor.replace(")V", "Ljava/lang/String;)V"), false);
                        return;
                    }
                } else if ("java/util/concurrent/TimeUnit".equals(owner) && "sleep".equals(name)
                        && "(J)V".equals(descriptor)) {
                    report(SLEEP, call);
                    if (rewrite) {
                        super.visitLdcInsn(where());
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CLOCK, "sleep",
                                "(Ljava/util/concurrent/TimeUnit;JLjava/lang/String;)V", false);
                        return;
                    }
                } else if ("java/lang/Thread".equals(owner)) {
                    if (name.startsWith("ofVirtual") || name.startsWith("ofPlatform")
                            || "startVirtualThread".equals(name)) {
                        escape(OWN_THREAD, call);
                    }
                } else if ("java/util/concurrent/Executors".equals(owner) && name.startsWith("new")) {
                    escape(OWN_EXECUTOR, call);
                } else if ("parallelStream".equals(name) && "()Ljava/util/stream/Stream;".equals(descriptor)
                        && owner.startsWith("java/util/")) {
                    // Collection.parallelStream runs on the common fork-join pool: rewritten to stream(), so the
                    // elements are handled in order by the calling task
                    report(SEQUENTIAL, call);
                    if (rewrite) {
                        super.visitMethodInsn(opcode, owner, "stream", descriptor, itf);
                        return;
                    }
                } else if ("java/util/concurrent/ForkJoinPool".equals(owner) && "commonPool".equals(name)) {
                    escape(DEFAULT_POOL, call);
                } else if ("java/util/concurrent/CompletableFuture".equals(owner)) {
                    if (name.endsWith("Async") && !descriptor.contains("Ljava/util/concurrent/Executor;")) {
                        escape(DEFAULT_POOL, call);
                    } else if ("delayedExecutor".equals(name)) {
                        escape(DEFAULT_POOL, call);
                    }
                } else if (owner.startsWith("java/time/") && "now".equals(name)
                        && !descriptor.startsWith("(Ljava/time/Clock;")) {
                    // now() of another java.time type (Year, YearMonth, MonthDay): not rewritten
                    escape(WALL_CLOCK, call);
                } else if ("reactor/core/scheduler/Schedulers".equals(owner)) {
                    escape(REACTOR, call);
                } else if (("reactor/core/publisher/Mono".equals(owner) || "reactor/core/publisher/Flux".equals(owner))
                        && (name.startsWith("delay") || "interval".equals(name) || "timeout".equals(name)
                                || "publishOn".equals(name) || "subscribeOn".equals(name))) {
                    escape(REACTOR, call);
                } else if (("reactor/core/publisher/Mono".equals(owner) || "reactor/core/publisher/Flux".equals(owner))
                        && name.startsWith("block")) {
                    escape(BLOCKING, call);
                } else if ("java/lang/Object".equals(owner) && "wait".equals(name)
                        || "java/util/concurrent/locks/LockSupport".equals(owner) && name.startsWith("park")
                        || "java/util/concurrent/locks/Condition".equals(owner) && name.startsWith("await")) {
                    escape(BLOCKING, call);
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, itf);
            }
        }
    }

    /**
     * The method of {@link DstWaits} (name and descriptor) that replaces this blocking wait, or null. The receiver
     * becomes the first argument and the call site is added as the last.
     */
    static String[] waitTarget(String owner, String name, String descriptor) {
        boolean untimed = descriptor.startsWith("()");
        boolean timed = descriptor.startsWith("(J" + TIME_UNIT + ")");
        if (!untimed && !timed) {
            return null;
        }
        String target = null;
        String receiver = owner;
        if ("java/util/concurrent/CompletableFuture".equals(owner)) {
            target = "get".equals(name) ? "get" : "join".equals(name) && untimed ? "join" : null;
        } else if (FUTURES.contains(owner) && "get".equals(name)) {
            target = "futureGet";
            receiver = "java/util/concurrent/Future";
        } else if ("java/util/concurrent/CountDownLatch".equals(owner) && "await".equals(name)) {
            target = "await";
        } else if ("java/util/concurrent/Semaphore".equals(owner)) {
            target = "acquire".equals(name) && untimed ? "acquire" : "tryAcquire".equals(name) && timed ? "tryAcquire" : null;
        } else if ("java/util/concurrent/DelayQueue".equals(owner) || QUEUES.contains(owner)) {
            target = "take".equals(name) && untimed ? "take" : "poll".equals(name) && timed ? "poll" : null;
            if (!"java/util/concurrent/DelayQueue".equals(owner)) {
                receiver = "java/util/concurrent/BlockingQueue";
            }
        }
        if (target == null) {
            return null;
        }
        String args = descriptor.substring(1, descriptor.indexOf(')'));
        String result = descriptor.substring(descriptor.indexOf(')') + 1);
        return new String[] { target, "(L" + receiver + ";" + args + "Ljava/lang/String;)" + result };
    }

    /**
     * The method of {@link DstClock} that replaces {@code now()} or {@code now(ZoneId)} of this {@code java.time} type,
     * or null.
     */
    static String clockTarget(String owner, String descriptor) {
        String type = owner.substring("java/time/".length());
        String target = switch (type) {
            case "Instant" -> "()Ljava/time/Instant;".equals(descriptor) ? "instantNow" : null;
            case "LocalDateTime", "ZonedDateTime", "OffsetDateTime", "LocalDate", "LocalTime", "OffsetTime" ->
                ("()L" + owner + ";").equals(descriptor) || ("(Ljava/time/ZoneId;)L" + owner + ";").equals(descriptor)
                        ? Character.toLowerCase(type.charAt(0)) + type.substring(1) + "Now" : null;
            default -> null;
        };
        return target;
    }

    /** The classes and packages outside the code under test whose time calls are rewritten too. */
    public static Set<String> alsoRewritten() {
        Set<String> answer = new LinkedHashSet<>();
        ALSO.forEach(p -> answer.add(p.endsWith("/") ? p.substring(0, p.length() - 1).replace('/', '.') : p.replace('/', '.')));
        return answer;
    }

    /** The packages whose time calls are rewritten. */
    public static Set<String> packages() {
        Set<String> answer = new LinkedHashSet<>();
        PREFIXES.forEach(p -> answer.add(p.replace('/', '.').substring(0, p.length() - 1)));
        return answer;
    }
}
