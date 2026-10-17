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
package org.apache.camel.test.dst.sample;

import java.util.concurrent.CompletableFuture;

/**
 * Code under test with escape points the harness does not control, for the strict mode test.
 */
public final class SampleEscapes {

    private SampleEscapes() {
    }

    /** Creates its own thread (not started). */
    public static Thread ownThread() {
        return new Thread(() -> {
        }, "sample");
    }

    /** Runs a task on the common fork-join pool. */
    public static CompletableFuture<Void> commonPool(Runnable task) {
        return CompletableFuture.runAsync(task);
    }
}
