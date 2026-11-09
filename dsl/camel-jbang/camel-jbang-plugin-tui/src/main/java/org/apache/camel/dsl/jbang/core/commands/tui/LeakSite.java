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

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.table.Cell;

/**
 * Where the objects of a sample of the Memory Leak tab were allocated, in the code of the integration: the first frame
 * of their stack that is not of the JDK or of Camel. Two leaks of byte[] are two lines that say byte[]; their sites
 * (MemoryLeak.lambda$configure$0:21, BufferLeakProcessor.process:45) tell them apart.
 */
final class LeakSite {

    private static final List<String> FRAMEWORK = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.", "org.apache.camel.");

    private LeakSite() {
    }

    /** The first frame of the stack in the code of the integration, method:line; null when there is none. */
    static String of(List<MemoryLeakTab.StackEntry> stack) {
        if (stack == null) {
            return null;
        }
        for (MemoryLeakTab.StackEntry se : stack) {
            String m = se.method;
            // the class of a lambda is generated (MemoryLeak$$Lambda.0x...): its body is the frame before
            if (m == null || m.contains("$$Lambda") || FRAMEWORK.stream().anyMatch(m::startsWith)) {
                continue;
            }
            return se.line > 0 ? m + ":" + se.line : m;
        }
        return null;
    }

    /** The class of the objects, and where they were allocated after it. */
    static Cell classCell(String className, List<MemoryLeakTab.StackEntry> stack) {
        String site = of(stack);
        Span cls = Span.styled(className != null ? className : "", Style.EMPTY.fg(Theme.accent()));
        return site == null ? Cell.from(cls) : Cell.from(Line.from(cls, Span.styled("  in " + site, Theme.muted())));
    }
}
