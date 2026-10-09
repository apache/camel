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

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import run.endive.runtime.ExportFunction;
import run.endive.runtime.Instance;
import run.endive.wasm.WasmModule;

public class WasmFunction implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(WasmFunction.class);

    private final Lock lock;

    private final WasmModule module;
    private final String functionName;

    // guarded by lock; null after a failed call until the next call creates a new instance
    private Instance instance;
    private ExportFunction function;
    private ExportFunction alloc;
    private ExportFunction dealloc;

    public WasmFunction(WasmModule module, String functionName) {
        this.lock = new ReentrantLock();

        this.module = Objects.requireNonNull(module);
        this.functionName = Objects.requireNonNull(functionName);

        // the instance fields are not final: create the first instance under the lock, so that a first run() on
        // another thread sees them
        lock.lock();
        try {
            createInstance();
        } finally {
            lock.unlock();
        }
    }

    public byte[] run(byte[] in) throws Exception {
        Objects.requireNonNull(in);

        final int inSize = in.length;
        final byte[] out;
        final boolean error;

        //
        // Wasm execution is not thread safe so we must put a
        // synchronization guard around the function execution
        //
        lock.lock();
        try {
            if (instance == null) {
                createInstance();
            }

            try {
                int inPtr = (int) alloc.apply(inSize)[0];
                instance.memory().write(inPtr, in);

                long[] results = function.apply(inPtr, inSize);
                long ptrAndSize = results[0];

                int outPtr = (int) (ptrAndSize >> 32);
                int outSize = (int) ptrAndSize;

                // assume the max output is 31 bit, leverage the first bit for
                // error detection
                error = isError(outSize);
                if (error) {
                    outSize = errSize(outSize);
                }

                out = instance.memory().readBytes(outPtr, outSize);

                dealloc.apply(inPtr, inSize);
                dealloc.apply(outPtr, outSize);
            } catch (RuntimeException | Error e) {
                // The guest trapped, was interrupted or failed otherwise: its memory and globals are left as the
                // failed call left them, and whatever it allocated before the failure can never be released.
                // Discard the instance instead of calling into it again, so the original exception is propagated
                // as is, and create a new one on the next call.
                discardInstance(e);
                throw e;
            }
        } finally {
            lock.unlock();
        }

        if (error) {
            throw new RuntimeException(new String(out, StandardCharsets.UTF_8));
        }

        return out;
    }

    @Override
    public void close() throws Exception {
    }

    private void createInstance() {
        Instance newInstance = Instance.builder(this.module).build();

        this.function = newInstance.export(this.functionName);
        this.alloc = newInstance.export(Wasm.FN_ALLOC);
        this.dealloc = newInstance.export(Wasm.FN_DEALLOC);
        this.instance = newInstance;
    }

    private void discardInstance(Throwable cause) {
        LOG.debug("Discarding the Wasm instance of function {} after a failed call: {}", functionName, cause.getMessage());

        this.instance = null;
        this.function = null;
        this.alloc = null;
        this.dealloc = null;
    }

    private static boolean isError(int number) {
        return (number & (1 << 31)) != 0;
    }

    private static int errSize(int number) {
        return number & (~(1 << 31));
    }
}
