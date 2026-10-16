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
package org.apache.camel.test.dst;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Escape points: places where the code under test leaves the harness's control. Two sources:
 * <ul>
 * <li>loaded: call sites found while rewriting the classes of the instrumented packages (see
 * {@link DstInstrumentation});</li>
 * <li>runtime: the harness saw a task submitted, a clock read or a wait from a thread it does not run, or a thread that
 * was started during the runs.</li>
 * </ul>
 */
public final class DstEscapes {

    private static final Set<String> LOADED = Collections.synchronizedSet(new TreeSet<>());
    private static final Set<String> RUNTIME = Collections.synchronizedSet(new LinkedHashSet<>());

    private DstEscapes() {
    }

    static void recordLoaded(String entry) {
        LOADED.add(entry);
    }

    public static void recordRuntime(String entry) {
        if (RUNTIME.size() < 1000) {
            RUNTIME.add(entry);
        }
    }

    public static List<String> loaded() {
        synchronized (LOADED) {
            return new ArrayList<>(LOADED);
        }
    }

    public static List<String> runtime() {
        synchronized (RUNTIME) {
            return new ArrayList<>(RUNTIME);
        }
    }

    /** Names of the live threads, to find threads started by the code under test. */
    public static Set<String> liveThreads() {
        Set<String> names = new TreeSet<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive()) {
                names.add(t.getName());
            }
        }
        return names;
    }
}
