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

import java.util.Objects;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Module;
import com.dylibso.chicory.wasm.types.Value;

public class WasmFunction implements AutoCloseable {
    private final Object lock;

    private final Module module;
    private final String functionName;

    private final Instance instance;
    private final ExportFunction function;
    private final ExportFunction alloc;
    private final ExportFunction dealloc;

    public WasmFunction(Module module, String functionName) {
        this.lock = new Object();

        this.module = Objects.requireNonNull(module);
        this.functionName = Objects.requireNonNull(functionName);

        this.instance = this.module.instantiate();
        this.function = this.instance.export(this.functionName);
        this.alloc = this.instance.export(Wasm.FN_ALLOC);
        this.dealloc = this.instance.export(Wasm.FN_DEALLOC);
    }

    public byte[] run(byte[] in) throws Exception {
        Objects.requireNonNull(in);

        int inPtr = -1;
        int inSize = in.length;
        int outPtr = -1;
        int outSize = 0;

        //
        // Wasm execution is not thread safe so we must put a
        // synchronization guard around the function execution
        //
        synchronized (lock) {
            try {
                inPtr = alloc.apply(Value.i32(inSize))[0].asInt();
                instance.memory().write(inPtr, in);

                Value[] results = function.apply(Value.i32(inPtr), Value.i32(inSize));
                long ptrAndSize = results[0].asLong();

                outPtr = (int) (ptrAndSize >> 32);
                outSize = (int) ptrAndSize;

                // assume the max output is 31 bit, leverage the first bit for
                // error detection
                if (isError(outSize)) {
                    int errSize = errSize(outSize);
                    String errData = instance.memory().readString(outPtr, errSize);

                    throw new RuntimeException(errData);
                }

                return instance.memory().readBytes(outPtr, outSize);
            } finally {
                if (inPtr != -1) {
                    dealloc.apply(Value.i32(inPtr), Value.i32(inSize));
                }
                if (outPtr != -1) {
                    dealloc.apply(Value.i32(outPtr), Value.i32(outSize));
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
    }

    private static boolean isError(int number) {
        return (number & (1 << 31)) != 0;
    }

    private static int errSize(int number) {
        return number & (~(1 << 31));
    }
}
