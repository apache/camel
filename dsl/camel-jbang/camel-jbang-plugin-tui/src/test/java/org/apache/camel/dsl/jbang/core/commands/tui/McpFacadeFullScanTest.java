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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An agent reading the Overview from another tab gets the data of every integration as of now, not as of the last time
 * the Overview was shown: the read asks for a scan of every integration and waits for it.
 */
class McpFacadeFullScanTest {

    @Test
    void theReadWaitsForTheNextFullScan() {
        AtomicLong done = new AtomicLong();
        McpFacade facade = new McpFacade(
                null, new AtomicReference<>(List.of()), null, null, null, null, null, null, null, null, null, null,
                null);
        // the refresh scans every integration a little later, on its own thread
        facade.setFullScan(
                () -> CompletableFuture.runAsync(done::incrementAndGet, CompletableFuture.delayedExecutor(200,
                        TimeUnit.MILLISECONDS)),
                done::get);

        facade.awaitFullScan();

        assertThat(done.get()).isEqualTo(1);
    }
}
