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
package org.apache.camel.spi;

import java.util.ArrayList;
import java.util.List;

/**
 * Strategy for reifiers.
 */
public abstract class ReifierStrategy {

    private static final List<Runnable> CLEARERS = new ArrayList<>();

    public static void addReifierClearer(Runnable strategy) {
        CLEARERS.add(strategy);
    }

    /**
     * DANGER: Clears the refifiers map.
     * After this the JVM with Camel cannot add new routes (using same classloader to load this class).
     * Clearing this map allows Camel to reduce memory footprint.
     */
    public static void clearReifiers() {
        CLEARERS.forEach(Runnable::run);
        CLEARERS.clear();
    }

}
