/**
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
package org.apache.camel.util;

import java.util.concurrent.atomic.AtomicLong;

public final class ReferenceCount {
    private final AtomicLong count;
    private final Runnable onFirst;
    private final Runnable onRelease;

    private ReferenceCount(Runnable onFirst, Runnable onRelease) {
        this.count = new AtomicLong(0);
        this.onFirst = ObjectHelper.notNull(onFirst, "onFirst");
        this.onRelease = ObjectHelper.notNull(onRelease, "onRelease");
    }

    /**
     * Returns the reference count.
     */
    public long get() {
        return count.get();
    }

    /**
     * Increases the reference count invoke onFirst on the first increment;
     */
    public void retain() throws IllegalStateException {
        while (true) {
            long v = count.get();
            if (v < 0) {
                throw new IllegalStateException("Released");
            }

            if (count.compareAndSet(v, v + 1)) {
                if (v == 0) {
                    this.onFirst.run();
                }

                break;
            }
        }
    }

    /**
     * Decreases the reference count and invoke onRelease if the reference count reaches {@code 0}.
     */
    public void release() throws IllegalStateException {
        while (true) {
            long v = count.get();
            if (v <= 0) {
                throw new IllegalStateException("ReferenceCount already released");
            }

            if (count.compareAndSet(v, v - 1)) {
                if (v == 1) {
                    onRelease.run();
                }

                break;
            }
        }
    }

    // *******************************
    // Helpers
    // *******************************

    public static ReferenceCount on(Runnable onFirst, Runnable onRelease) {
        return new ReferenceCount(onFirst, onRelease);
    }

    public static ReferenceCount onRelease(Runnable onRelease) {
        return new ReferenceCount(() -> { }, onRelease);
    }
}
