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
package org.apache.camel.dsl.jbang.core.commands;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel run with --jvm-args or --jvm-debug runs the integration in a JVM of its own, in the foreground. A kill of camel
 * run (SIGTERM) left that JVM running without its parent (CAMEL-26930); it is now stopped by a shutdown hook.
 */
class RunChildStopTest {

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void theHookStopsTheChild() throws Exception {
        Process child = new ProcessBuilder("sleep", "60").start();
        try {
            Thread hook = Run.childStopHook(child);
            hook.start();
            hook.join(TimeUnit.SECONDS.toMillis(20));

            assertThat(child.waitFor(10, TimeUnit.SECONDS)).as("the child is stopped").isTrue();
        } finally {
            child.destroyForcibly();
        }
    }
}
