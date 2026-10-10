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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.TrapException;
import run.endive.wasi.WasiExitException;
import run.endive.wasi.WasiOptions;
import run.endive.wasi.WasiPreview1;
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
 * variables it is given: no preopened directories (so no files) and no sockets. Clock and random numbers are the
 * host's.
 */
public final class WasiCommand {

    private static final String START = "_start";
    private static final String WASI_MODULE = "wasi_snapshot_preview1";

    private final WasmModule module;
    private final List<String> arguments;
    private final MemoryLimits memoryLimits;

    /**
     * @param  module                   the parsed module
     * @param  arguments                the program's arguments, the program name first
     * @param  maxMemoryPages           the maximum number of 64 KiB pages the module's memory may grow to, or 0 to keep
     *                                  the maximum the module declares
     * @throws IllegalArgumentException if the module is not a WASI Preview 1 command module, or maxMemoryPages is lower
     *                                  than the initial memory the module declares
     */
    public WasiCommand(WasmModule module, List<String> arguments, int maxMemoryPages) {
        this.module = Objects.requireNonNull(module);
        this.arguments = List.copyOf(arguments);
        validate(module);
        this.memoryLimits = memoryLimits(module, maxMemoryPages);
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
        // built on the thread that runs the program: the default random number generator is the thread's own.
        // Endive reports the standard streams as terminals unless told otherwise; they are not, and a program that
        // sees a terminal may add terminal output, such as colours, to the message body
        WasiOptions.Builder options = WasiOptions.builder()
                .withStdin(stdin, false)
                .withStdout(stdout, false)
                .withStderr(stderr, false)
                .withArguments(arguments)
                .withThrowOnExit0(true);
        environment.forEach(options::withEnvironment);

        Instance instance = null;
        try (WasiPreview1 wasi = WasiPreview1.builder().withOptions(options.build()).build()) {
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

    private Instance newInstance(ImportValues imports) {
        Instance.Builder builder = Instance.builder(module)
                .withImportValues(imports)
                // _start is called explicitly, so that its exit code and traps are handled in one place
                .withStart(false);
        if (memoryLimits != null) {
            builder.withMemoryLimits(memoryLimits);
        }
        return builder.build();
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
        try {
            if (instance != null && instance.memory() != null
                    && instance.memory().pages() >= instance.memory().maximumPages()) {
                return " (the module's memory is at its maximum of " + instance.memory().maximumPages()
                       + " pages, see the maxMemoryPages option)";
            }
        } catch (RuntimeException e) {
            // the module has no memory: no hint
        }
        return null;
    }
}
