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
package org.apache.camel.wasm;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import run.endive.compiler.InterpreterFallback;
import run.endive.compiler.MachineFactoryCompiler;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.Machine;
import run.endive.runtime.Memory;
import run.endive.runtime.TrapException;
import run.endive.wasi.WasiExitException;
import run.endive.wasi.WasiOptions;
import run.endive.wasi.WasiPreview1;
import run.endive.wasm.WasmEngineException;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.Export;
import run.endive.wasm.types.ExportSection;
import run.endive.wasm.types.ExternalType;
import run.endive.wasm.types.MemoryLimits;
import run.endive.wasm.types.MemorySection;

/**
 * Runs a WASI Preview 1 command module (a program with a {@code _start} function, such as a Rust program built for
 * {@code wasm32-wasip1} or a Go program built with {@code GOOS=wasip1 GOARCH=wasm}) to completion.
 * <p/>
 * The module is parsed once by the caller and every {@link #run} creates a new instance of it, so runs never share
 * guest memory or globals and need no lock. A run gets exactly the standard streams, arguments and environment
 * variables it is given: no preopened directories (so no files) and no sockets. The clock is the host's, and
 * {@code random_get} is served by a {@link SecureRandom} of the host.
 * <p/>
 * The module is interpreted, unless the command is created with {@code compile} set: then the module is translated to
 * JVM bytecode once, in the constructor, and every run executes that code on its own instance. The generated classes
 * belong to the command: they can be unloaded when the command is no longer reachable.
 */
public final class WasiCommand {

    private static final Logger LOG = LoggerFactory.getLogger(WasiCommand.class);

    private static final String START = "_start";
    private static final String WASI_MODULE = "wasi_snapshot_preview1";

    /**
     * The source of WASI {@code random_get}, which guests use for keys, nonces, UUIDs and hash seeds (Go
     * {@code crypto/rand}, Rust {@code getrandom}). Endive's default is {@code ThreadLocalRandom}, which is
     * predictable. A SecureRandom is thread-safe, so one is shared by all runs.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WasmModule module;
    private final List<String> arguments;
    private final MemoryLimits memoryLimits;
    /** Creates the compiled code of the module for an instance; null when the module is interpreted. */
    private final Function<Instance, Machine> machineFactory;
    private final boolean interpreterFallback;

    /**
     * Creates a command that interprets the module.
     *
     * @param  module                   the parsed module
     * @param  arguments                the program's arguments, the program name first
     * @param  maxMemoryPages           the maximum number of 64 KiB pages the module's memory may grow to, or 0 to keep
     *                                  the maximum the module declares
     * @throws IllegalArgumentException if the module is not a WASI Preview 1 command module, or maxMemoryPages is lower
     *                                  than the initial memory the module declares
     */
    public WasiCommand(WasmModule module, List<String> arguments, int maxMemoryPages) {
        this(module, arguments, maxMemoryPages, false);
    }

    /**
     * @param  module                   the parsed module
     * @param  arguments                the program's arguments, the program name first
     * @param  maxMemoryPages           the maximum number of 64 KiB pages the module's memory may grow to, or 0 to keep
     *                                  the maximum the module declares
     * @param  compile                  whether to translate the module to JVM bytecode now, with the Endive runtime
     *                                  compiler, instead of interpreting it. This defines classes at run time, which a
     *                                  GraalVM native image cannot do. A function that is too large for a JVM method is
     *                                  left to the interpreter, which is logged once, at WARN
     * @throws IllegalArgumentException if the module is not a WASI Preview 1 command module, or maxMemoryPages is lower
     *                                  than the initial memory the module declares
     * @throws IllegalStateException    if compile is set and the Endive runtime compiler is not on the classpath, or
     *                                  cannot compile the module. The message names the module by its program name (the
     *                                  first argument)
     */
    public WasiCommand(WasmModule module, List<String> arguments, int maxMemoryPages, boolean compile) {
        this.module = Objects.requireNonNull(module);
        this.arguments = List.copyOf(arguments);
        validate(module);
        this.memoryLimits = memoryLimits(module, maxMemoryPages);

        Function<Instance, Machine> factory = null;
        boolean fallback = false;
        if (compile) {
            String name = this.arguments.isEmpty() ? "(no name)" : this.arguments.get(0);
            try {
                try {
                    factory = RuntimeCompiler.compile(module, false);
                } catch (WasmEngineException e) {
                    // Endive could not compile every function. When the reason is a function that is too large for a
                    // JVM method, the module still works with that function interpreted, so compile again with the
                    // interpreter as fallback. For any other reason a second attempt would fail as the first one did.
                    if (!RuntimeCompiler.isFunctionTooLarge(e)) {
                        throw e;
                    }
                    factory = RuntimeCompiler.compile(module, true);
                    fallback = true;
                    LOG.warn("The Wasm module {} is compiled in part only: it has functions that are too large for a"
                             + " JVM method, which are interpreted",
                            name);
                    LOG.debug("The Endive compiler reported for the Wasm module {}: {}", name, e.getMessage());
                }
            } catch (NoClassDefFoundError e) {
                throw new IllegalStateException(
                        "compile=true needs the Endive runtime compiler on the classpath (run.endive:compiler, a dependency"
                                                + " of camel-wasm, and its ASM dependencies), but a class is missing: "
                                                + e.getMessage(),
                        e);
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "The Wasm module " + name + " cannot be compiled to JVM bytecode (compile=true): " + e.getMessage(),
                        e);
            }
        }
        this.machineFactory = factory;
        this.interpreterFallback = fallback;
    }

    /**
     * Whether the module was compiled to JVM bytecode; otherwise it is interpreted.
     */
    public boolean isCompiled() {
        return machineFactory != null;
    }

    /**
     * Whether the module was compiled with some of its functions left to the interpreter, because they are too large
     * for a JVM method. Package-private for tests only.
     */
    boolean usesInterpreterFallback() {
        return interpreterFallback;
    }

    /**
     * Runs the program once, on a new instance, on the calling thread.
     * <p/>
     * A {@link RuntimeException} thrown by one of the given streams stops the program and is rethrown.
     *
     * @param  stdin                                       the program's standard input
     * @param  stdout                                      receives the program's standard output
     * @param  stderr                                      receives the program's standard error
     * @param  environment                                 the program's complete environment
     * @return                                             the exit code: 0 when {@code _start} returned, otherwise the
     *                                                     value passed to {@code proc_exit}
     * @throws TrapException                               if the program trapped (for example a Rust panic)
     * @throws run.endive.runtime.WasmInterruptedException if the calling thread was interrupted
     */
    public int run(InputStream stdin, OutputStream stdout, OutputStream stderr, Map<String, String> environment) {
        WasiOptions options = options(stdin, stdout, stderr, environment);

        Instance instance = null;
        try (WasiPreview1 wasi = WasiPreview1.builder().withOptions(options).build()) {
            instance = newInstance(ImportValues.builder().addFunction(wasi.toHostFunctions()).build());
            instance.export(START).apply();
            return 0;
        } catch (WasiExitException e) {
            return e.exitCode();
        } catch (TrapException e) {
            String hint = memoryHint(instance);
            if (hint == null) {
                throw e;
            }
            TrapException trap = new TrapException(e.getMessage() + hint);
            trap.initCause(e);
            throw trap;
        }
    }

    /**
     * The WASI context of one run. Package-private for tests only.
     */
    WasiOptions options(InputStream stdin, OutputStream stdout, OutputStream stderr, Map<String, String> environment) {
        // Endive reports the standard streams as terminals unless told otherwise; they are not, and a program that
        // sees a terminal may add terminal output, such as colours, to the message body
        WasiOptions.Builder options = WasiOptions.builder()
                .withRandom(RANDOM)
                .withStdin(stdin, false)
                .withStdout(stdout, false)
                .withStderr(stderr, false)
                .withArguments(arguments)
                .withThrowOnExit0(true);
        environment.forEach(options::withEnvironment);
        return options.build();
    }

    private Instance newInstance(ImportValues imports) {
        Instance.Builder builder = Instance.builder(module)
                .withImportValues(imports)
                // _start is called explicitly, so that its exit code and traps are handled in one place
                .withStart(false);
        if (memoryLimits != null) {
            builder.withMemoryLimits(memoryLimits);
        }
        if (machineFactory != null) {
            builder.withMachineFactory(machineFactory);
        }
        return builder.build();
    }

    /**
     * The only class that refers to the Endive runtime compiler, so that this command can be loaded and used with the
     * interpreter when the compiler is not on the classpath.
     */
    static final class RuntimeCompiler {

        // ASM's org.objectweb.asm.MethodTooLargeException, compared by name: the class is a dependency of the compiler
        private static final String METHOD_TOO_LARGE = "org.objectweb.asm.MethodTooLargeException";

        /**
         * Compiles the module. A function that is too large for a JVM method either fails the compilation, or is left
         * to the interpreter without a message. Endive's own default (InterpreterFallback.WARN) is not used: it reports
         * the fallback on System.err, past the logging of the application.
         */
        static Function<Instance, Machine> compile(WasmModule module, boolean interpretTooLargeFunctions) {
            return MachineFactoryCompiler.builder(module)
                    .withInterpreterFallback(
                            interpretTooLargeFunctions ? InterpreterFallback.SILENT : InterpreterFallback.FAIL)
                    .compile();
        }

        /**
         * Whether the compilation failed because a function is too large for a JVM method: Endive reports that with a
         * WasmEngineException caused by the MethodTooLargeException of ASM.
         */
        static boolean isFunctionTooLarge(Throwable failure) {
            Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Throwable t = failure; t != null && seen.add(t); t = t.getCause()) {
                if (METHOD_TOO_LARGE.equals(t.getClass().getName())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void validate(WasmModule module) {
        ExportSection exports = module.exportSection();
        boolean hasStart = false;
        for (int i = 0; i < exports.exportCount(); i++) {
            Export export = exports.getExport(i);
            if (START.equals(export.name()) && export.exportType() == ExternalType.FUNCTION) {
                hasStart = true;
                break;
            }
        }
        if (!hasStart) {
            throw new IllegalArgumentException(
                    "The module has no " + START + " function, so it is not a WASI Preview 1 command module."
                                               + " Build it as a program, for example with cargo build --target"
                                               + " wasm32-wasip1 or GOOS=wasip1 GOARCH=wasm go build");
        }
        String unsupported = module.importSection().stream()
                .filter(i -> !WASI_MODULE.equals(i.module()))
                .map(i -> i.module() + "." + i.name())
                .distinct()
                .collect(Collectors.joining(", "));
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(
                    "The module imports from modules other than " + WASI_MODULE + ", which cannot be provided: "
                                               + unsupported);
        }
    }

    private static MemoryLimits memoryLimits(WasmModule module, int maxMemoryPages) {
        if (maxMemoryPages <= 0) {
            return null;
        }
        Optional<MemorySection> section = module.memorySection();
        if (section.isEmpty() || section.get().memoryCount() == 0) {
            // the module defines no memory of its own, so there is nothing to limit
            return null;
        }
        MemoryLimits declared = section.get().getMemory(0).limits();
        if (maxMemoryPages < declared.initialPages()) {
            throw new IllegalArgumentException(
                    "maxMemoryPages (" + maxMemoryPages + ") is lower than the " + declared.initialPages()
                                               + " pages of memory the module declares as its initial size");
        }
        return new MemoryLimits(declared.initialPages(), Math.min(maxMemoryPages, declared.maximumPages()));
    }

    /**
     * A hint for the message of a trap when the memory is at its maximum, the usual reason a module traps after
     * maxMemoryPages was lowered: its allocator could not grow the memory and aborted. Null when there is no hint.
     */
    private static String memoryHint(Instance instance) {
        // the memory is null when the module has none
        Memory memory = instance != null ? instance.memory() : null;
        if (memory != null && memory.pages() >= memory.maximumPages()) {
            return " (the module's memory is at its maximum of " + memory.maximumPages()
                   + " pages, see the maxMemoryPages option)";
        }
        return null;
    }
}
