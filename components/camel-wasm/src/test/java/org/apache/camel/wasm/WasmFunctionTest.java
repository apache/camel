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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import run.endive.runtime.TrapException;
import run.endive.runtime.WasmInterruptedException;
import run.endive.wabt.Wat2Wasm;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

public class WasmFunctionTest {

    /**
     * A guest with one page of memory (64 KiB) and an arena allocator that only gets its memory back when every
     * allocation has been released. The process function looks at the first byte of its input:
     * <ul>
     * <li>{@code t} allocates 32 KiB and traps before returning, like a Rust panic on bad input</li>
     * <li>{@code e} returns the error message {@code boom} with the error bit set on its size</li>
     * <li>{@code s} spins until the calling thread is interrupted</li>
     * <li>anything else is echoed back</li>
     * </ul>
     * The dealloc function traps when it is given a size that does not fit in 31 bits.
     */
    private static final String GUEST = """
            (module
              (memory (export "memory") 1 1)
              (data (i32.const 16) "boom")
              (global $base i32 (i32.const 1024))
              (global $next (mut i32) (i32.const 1024))
              (global $live (mut i32) (i32.const 0))

              (func $alloc (export "alloc") (param $size i32) (result i32)
                (local $ptr i32)
                (local.set $ptr (global.get $next))
                (if (i32.gt_u (i32.add (local.get $ptr) (local.get $size)) (i32.const 65536))
                  (then unreachable))
                (global.set $next (i32.add (local.get $ptr) (local.get $size)))
                (global.set $live (i32.add (global.get $live) (i32.const 1)))
                (local.get $ptr))

              (func (export "dealloc") (param $ptr i32) (param $len i32)
                (if (i32.lt_s (local.get $len) (i32.const 0))
                  (then unreachable))
                (global.set $live (i32.sub (global.get $live) (i32.const 1)))
                (if (i32.eqz (global.get $live))
                  (then (global.set $next (global.get $base)))))

              (func (export "process") (param $ptr i32) (param $len i32) (result i64)
                (local $op i32)
                (local $out i32)
                (if (i32.gt_u (local.get $len) (i32.const 0))
                  (then (local.set $op (i32.load8_u (local.get $ptr)))))
                (if (i32.eq (local.get $op) (i32.const 116))
                  (then
                    (drop (call $alloc (i32.const 32768)))
                    unreachable))
                (if (i32.eq (local.get $op) (i32.const 101))
                  (then
                    (local.set $out (call $alloc (i32.const 4)))
                    (memory.copy (local.get $out) (i32.const 16) (i32.const 4))
                    (return (i64.or
                      (i64.shl (i64.extend_i32_u (local.get $out)) (i64.const 32))
                      (i64.const 0x80000004)))))
                (if (i32.eq (local.get $op) (i32.const 115))
                  (then (loop $spin (br $spin))))
                (local.set $out (call $alloc (local.get $len)))
                (memory.copy (local.get $out) (local.get $ptr) (local.get $len))
                (i64.or
                  (i64.shl (i64.extend_i32_u (local.get $out)) (i64.const 32))
                  (i64.extend_i32_u (local.get $len))))
            )
            """;

    private static WasmFunction newFunction() {
        WasmModule module = Parser.parse(Wat2Wasm.parse(GUEST));
        return new WasmFunction(module, "process");
    }

    private static byte[] data(char first, int size) {
        byte[] data = new byte[size];
        Arrays.fill(data, (byte) 'a');
        data[0] = (byte) first;
        return data;
    }

    @Test
    public void testTrapDoesNotLeakGuestMemory() throws Exception {
        WasmFunction function = newFunction();

        // each trap leaves 32 KiB allocated in the guest, more than half of its memory
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> function.run(data('t', 1)))
                    .isInstanceOf(TrapException.class);
        }

        // needs 2 x 16 KiB: only fits if the memory of the failed calls was given back
        byte[] in = data('a', 16 * 1024);
        assertThat(function.run(in)).isEqualTo(in);
    }

    @Test
    public void testErrorIsDeallocatedWithItsSize() throws Exception {
        WasmFunction function = newFunction();

        // dealloc traps on a size with the error bit set, which would replace the error of the function
        assertThatThrownBy(() -> function.run(data('e', 1)))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        byte[] in = data('a', 16);
        assertThat(function.run(in)).isEqualTo(in);
    }

    @Test
    public void testInterruptDoesNotLeakGuestMemory() throws Exception {
        WasmFunction function = newFunction();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // the input of the interrupted call takes 40 KiB of the guest memory until it is released
        Thread caller = new Thread(() -> {
            try {
                function.run(data('s', 40 * 1024));
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "wasm-caller");
        caller.start();

        await().atMost(10, TimeUnit.SECONDS).until(() -> isRunningGuestCode(caller));
        caller.interrupt();
        caller.join(TimeUnit.SECONDS.toMillis(10));

        assertThat(caller.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(WasmInterruptedException.class);

        // needs 2 x 16 KiB: only fits if the input of the interrupted call was given back
        byte[] in = data('a', 16 * 1024);
        assertThat(function.run(in)).isEqualTo(in);
    }

    private static boolean isRunningGuestCode(Thread thread) {
        return Arrays.stream(thread.getStackTrace())
                .anyMatch(e -> e.getClassName().startsWith("run.endive.runtime.InterpreterMachine"));
    }
}
