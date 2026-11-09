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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The memory-leak example leaks byte[] in two routes: the sites of the two samples tell them apart.
 */
class LeakSiteTest {

    @Test
    void theSiteIsTheFirstFrameOfTheCodeOfTheIntegration() {
        assertThat(LeakSite.of(List.of(
                frame("java.util.Arrays.copyOf", 3512),
                frame("BufferLeakProcessor.process", 45),
                frame("org.apache.camel.support.processor.DelegateSyncProcessor.process", 67))))
                .isEqualTo("BufferLeakProcessor.process:45");
        // a lambda: its body, not its generated class
        assertThat(LeakSite.of(List.of(
                frame("MemoryLeak.lambda$configure$0", 21),
                frame("MemoryLeak$$Lambda.0x000000006a812c00.process", -1),
                frame("org.apache.camel.support.processor.DelegateSyncProcessor.process", 67))))
                .isEqualTo("MemoryLeak.lambda$configure$0:21");
        // all of Camel and the JDK: no site
        assertThat(LeakSite.of(List.of(frame("org.apache.camel.util.IOHelper.copy", 10)))).isNull();
        assertThat(LeakSite.of(null)).isNull();
    }

    private static MemoryLeakTab.StackEntry frame(String method, int line) {
        MemoryLeakTab.StackEntry se = new MemoryLeakTab.StackEntry();
        se.method = method;
        se.line = line;
        return se;
    }
}
