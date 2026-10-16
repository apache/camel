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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.MethodTooLargeException;
import run.endive.runtime.TrapException;
import run.endive.wabt.Wat2Wasm;
import run.endive.wasi.WasiOptions;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmEngineException;
import run.endive.wasm.WasmModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WasiCommandTest {

    /** Writes "hi" to standard output. */
    private static final String HELLO = """
            (module
              (import "wasi_snapshot_preview1" "fd_write" (func $fd_write (param i32 i32 i32 i32) (result i32)))
              (memory (export "memory") 1)
              (data (i32.const 16) "hi")
              (func (export "_start")
                (i32.store (i32.const 0) (i32.const 16))
                (i32.store (i32.const 4) (i32.const 2))
                (drop (call $fd_write (i32.const 1) (i32.const 0) (i32.const 1) (i32.const 8)))))
            """;

    private static WasmModule module(String wat) {
        return Parser.parse(Wat2Wasm.parse(wat));
    }

    @Test
    void randomGetIsServedByASecureRandom() {
        WasiCommand command = new WasiCommand(module("(module (func (export \"_start\")))"), List.of("noop.wasm"), 0);

        WasiOptions options = command.options(
                InputStream.nullInputStream(), OutputStream.nullOutputStream(), OutputStream.nullOutputStream(), Map.of());

        // Endive's default is ThreadLocalRandom, which is predictable; guests use random_get for keys and nonces
        assertThat(options.random()).isInstanceOf(SecureRandom.class);
        assertThat(options.arguments()).containsExactly("noop.wasm");
    }

    @Test
    void trapOfAModuleWithoutMemory() {
        WasiCommand command
                = new WasiCommand(module("(module (func (export \"_start\") unreachable))"), List.of("trap.wasm"), 0);

        // the hint about a memory at its maximum does not apply, and looking for it must not fail
        assertThatThrownBy(() -> command.run(
                InputStream.nullInputStream(), OutputStream.nullOutputStream(), OutputStream.nullOutputStream(), Map.of()))
                .isInstanceOf(TrapException.class)
                .hasMessageNotContaining("maxMemoryPages");
    }

    @Test
    void compiledCodeIsGeneratedOnceAndRunsEveryInstance() {
        WasiCommand command = new WasiCommand(module(HELLO), List.of("hello.wasm"), 0, true);
        assertThat(command.isCompiled()).isTrue();

        GeneratedClasses first = new GeneratedClasses();
        GeneratedClasses second = new GeneratedClasses();
        assertThat(command.run(InputStream.nullInputStream(), first, OutputStream.nullOutputStream(), Map.of())).isZero();
        assertThat(command.run(InputStream.nullInputStream(), second, OutputStream.nullOutputStream(), Map.of())).isZero();

        // the two runs are separate instances, executed by the same generated classes: the module was compiled once
        assertThat(first.text()).isEqualTo("hi");
        assertThat(second.text()).isEqualTo("hi");
        assertThat(first.classes).isNotEmpty();
        assertThat(second.classes).isEqualTo(first.classes);
    }

    @Test
    void interpretedByDefault() {
        WasiCommand command = new WasiCommand(module(HELLO), List.of("hello.wasm"), 0);
        assertThat(command.isCompiled()).isFalse();

        GeneratedClasses out = new GeneratedClasses();
        assertThat(command.run(InputStream.nullInputStream(), out, OutputStream.nullOutputStream(), Map.of())).isZero();

        assertThat(out.text()).isEqualTo("hi");
        assertThat(out.classes).isEmpty();
    }

    @Test
    void compileWithoutTheCompilerOnTheClasspathFails() throws Exception {
        Class<?> isolated = new WithoutCompiler(WasiCommand.class.getClassLoader()).loadClass(WasiCommand.class.getName());
        assertThat(isolated).isNotSameAs(WasiCommand.class);
        var constructor = isolated.getConstructor(WasmModule.class, List.class, int.class, boolean.class);

        // the interpreter does not need the compiler
        assertThat(constructor.newInstance(module(HELLO), List.of("hello.wasm"), 0, false)).isNotNull();

        assertThatThrownBy(() -> constructor.newInstance(module(HELLO), List.of("hello.wasm"), 0, true))
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("compile=true needs the Endive runtime compiler on the classpath (run.endive:compiler")
                .hasMessageContaining("run/endive/compiler/MachineFactoryCompiler");
    }

    @Test
    void functionTooLargeForAJvmMethodIsInterpreted() {
        // 70000 times "i32.const 1, drop": valid, but more bytecode than a JVM method can hold (64 KB)
        byte[] code = new byte[3 * 70_000];
        for (int i = 0; i < code.length; i += 3) {
            code[i] = 0x41;
            code[i + 1] = 1;
            code[i + 2] = 0x1a;
        }
        WasmModule large = Parser.parse(startOnly(code));

        PrintStream systemErr = System.err;
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        WasiCommand command;
        System.setErr(new PrintStream(printed, true, StandardCharsets.UTF_8));
        try {
            command = new WasiCommand(large, List.of("large.wasm"), 0, true);
        } finally {
            System.setErr(systemErr);
        }

        // the module is compiled and works, with the large function interpreted; Endive did not report that on
        // System.err, as it does by default: the command logs it instead
        assertThat(command.isCompiled()).isTrue();
        assertThat(command.usesInterpreterFallback()).isTrue();
        assertThat(printed.toString(StandardCharsets.UTF_8)).doesNotContain("interpreted");
        assertThat(command.run(
                InputStream.nullInputStream(), OutputStream.nullOutputStream(), OutputStream.nullOutputStream(), Map.of()))
                .isZero();

        // a module without such a function is compiled as a whole
        assertThat(new WasiCommand(module(HELLO), List.of("hello.wasm"), 0, true).usesInterpreterFallback()).isFalse();
    }

    @Test
    void moduleThatCannotBeCompiledFailsWithItsName() {
        // a vector instruction (v128.const, then drop): valid, but not supported by the Endive compiler
        byte[] code = new byte[19];
        code[0] = (byte) 0xfd;
        code[1] = 0x0c;
        code[18] = 0x1a;
        WasmModule simd = Parser.parse(startOnly(code));

        assertThatThrownBy(() -> new WasiCommand(simd, List.of("simd.wasm"), 0, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("The Wasm module simd.wasm cannot be compiled to JVM bytecode (compile=true): ")
                .hasCauseInstanceOf(WasmEngineException.class)
                // the reason is not a function too large for a JVM method, so the module was not compiled a second
                // time with the interpreter as fallback
                .satisfies(e -> assertThat(WasiCommand.RuntimeCompiler.isFunctionTooLarge(e.getCause())).isFalse());
    }

    @Test
    void onlyAFunctionTooLargeForAJvmMethodIsCompiledAgainWithTheInterpreterAsFallback() {
        // how the Endive compiler reports a function too large for a JVM method
        MethodTooLargeException tooLarge = new MethodTooLargeException("Machine", "func_0", "()V", 70_000);
        WasmEngineException reported = new WasmEngineException("JVM bytecode too large for WASM method", tooLarge);
        assertThat(WasiCommand.RuntimeCompiler.isFunctionTooLarge(reported)).isTrue();
        assertThat(WasiCommand.RuntimeCompiler.isFunctionTooLarge(new RuntimeException(reported))).isTrue();

        // any other failure is not compiled again
        assertThat(WasiCommand.RuntimeCompiler.isFunctionTooLarge(new WasmEngineException("unsupported"))).isFalse();
        assertThat(WasiCommand.RuntimeCompiler.isFunctionTooLarge(
                new WasmEngineException("unsupported", new IllegalArgumentException())))
                .isFalse();
    }

    /**
     * A module in the binary format with one function, exported as _start, that has no parameters, results or locals
     * and the given instructions as its body. For bodies that are not practical to write in WAT.
     */
    private static byte[] startOnly(byte[] instructions) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0);
        body.writeBytes(instructions);
        body.write(0x0b);
        ByteArrayOutputStream code = new ByteArrayOutputStream();
        code.write(1);
        code.writeBytes(unsignedLeb128(body.size()));
        code.writeBytes(body.toByteArray());

        ByteArrayOutputStream module = new ByteArrayOutputStream();
        module.writeBytes(new byte[] { 0, 'a', 's', 'm', 1, 0, 0, 0 });
        // type section: one function type, no parameters and no results
        section(module, 1, new byte[] { 1, 0x60, 0, 0 });
        // function section: one function, of type 0
        section(module, 3, new byte[] { 1, 0 });
        // export section: function 0 as _start
        section(module, 7, new byte[] { 1, 6, '_', 's', 't', 'a', 'r', 't', 0, 0 });
        section(module, 10, code.toByteArray());
        return module.toByteArray();
    }

    private static void section(ByteArrayOutputStream module, int id, byte[] content) {
        module.write(id);
        module.writeBytes(unsignedLeb128(content.length));
        module.writeBytes(content);
    }

    private static byte[] unsignedLeb128(int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rest = value;
        do {
            int b = rest & 0x7f;
            rest >>>= 7;
            out.write(rest != 0 ? b | 0x80 : b);
        } while (rest != 0);
        return out.toByteArray();
    }

    /**
     * Standard output of a module that records, on every write, the classes on the call stack that were not loaded by
     * the class loader of the component: the classes the Endive compiler generated for the module.
     */
    private static final class GeneratedClasses extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final Set<Class<?>> classes = new HashSet<>();

        @Override
        public void write(int b) {
            write(new byte[] { (byte) b }, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            ClassLoader own = WasiCommand.class.getClassLoader();
            classes.addAll(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                            .filter(c -> c.getClassLoader() != null && c.getClassLoader() != own)
                            .takeWhile(c -> !c.getName().startsWith("org.junit."))
                            .collect(Collectors.toSet())));
            buffer.write(b, off, len);
        }

        String text() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * Loads its own copy of the classes of org.apache.camel.wasm, on a classpath without the Endive compiler.
     */
    private static final class WithoutCompiler extends ClassLoader {

        WithoutCompiler(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("run.endive.compiler.")) {
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith("org.apache.camel.wasm.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream is = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (is == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytes = is.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                return loaded;
            }
        }
    }
}
