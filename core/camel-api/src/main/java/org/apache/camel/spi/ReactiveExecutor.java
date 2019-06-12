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

import org.apache.camel.AsyncCallback;

/**
 * SPI to plugin different reactive engines in the Camel routing engine.
 */
public interface ReactiveExecutor {

    // TODO: Add javadoc
    // TODO: Better name

    default void schedule(Runnable runnable) {
        schedule(runnable, null);
    }

    void schedule(Runnable runnable, String description);

    default void scheduleMain(Runnable runnable) {
        scheduleMain(runnable, null);
    }

    void scheduleMain(Runnable runnable, String description);

    default void scheduleSync(Runnable runnable) {
        scheduleSync(runnable, null);
    }

    void scheduleSync(Runnable runnable, String description);

    // TODO: Can we make this so we dont need an method on this interface as its only used once
    boolean executeFromQueue();

    default void callback(AsyncCallback callback) {
        schedule(new Runnable() {

            @Override
            public void run() {
                callback.done(false);
            }

            @Override
            public String toString() {
                return "Callback[" + callback + "]";
            }
        });
    }

}
