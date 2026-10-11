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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import run.endive.wabt.Wat2Wasm;
import run.endive.wasi.WasiOptions;
import run.endive.wasm.Parser;

import static org.assertj.core.api.Assertions.assertThat;

class WasiCommandTest {

    @Test
    void randomGetIsServedByASecureRandom() {
        WasiCommand command = new WasiCommand(
                Parser.parse(Wat2Wasm.parse("(module (func (export \"_start\")))")), List.of("noop.wasm"), 0);

        WasiOptions options = command.options(
                InputStream.nullInputStream(), OutputStream.nullOutputStream(), OutputStream.nullOutputStream(), Map.of());

        // Endive's default is ThreadLocalRandom, which is predictable; guests use random_get for keys and nonces
        assertThat(options.random()).isInstanceOf(SecureRandom.class);
        assertThat(options.arguments()).containsExactly("noop.wasm");
    }
}
