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
package org.apache.camel.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

import org.apache.camel.util.function.ThrowingRunnable;
import org.apache.camel.util.function.ThrowingSupplier;

public final class LockHelper {
    private LockHelper() {
    }

    public static void doWithReadLock(StampedLock lock, Runnable task) {
        long stamp = lock.readLock();

        try {
            task.run();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public static <R> R callWithReadLock(StampedLock lock, Callable<R> task) throws Exception {
        long stamp = lock.readLock();

        try {
            return task.call();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public static <T extends Throwable> void doWithReadLockT(StampedLock lock, ThrowingRunnable<T> task) throws T {
        long stamp = lock.readLock();

        try {
            task.run();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public static <R> R supplyWithReadLock(StampedLock lock, Supplier<R> task)  {
        long stamp = lock.readLock();

        try {
            return task.get();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public static <R, T extends Throwable> R supplyWithReadLockT(StampedLock lock, ThrowingSupplier<R, T> task) throws T {
        long stamp = lock.readLock();

        try {
            return task.get();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public static void doWithWriteLock(StampedLock lock, Runnable task) {
        long stamp = lock.writeLock();

        try {
            task.run();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public static <R> R callWithWriteLock(StampedLock lock, Callable<R> task) throws Exception {
        long stamp = lock.writeLock();

        try {
            return task.call();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public static <R> R supplyWithWriteLock(StampedLock lock, Supplier<R> task)  {
        long stamp = lock.writeLock();

        try {
            return task.get();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public static <T extends Throwable> void doWithWriteLockT(StampedLock lock, ThrowingRunnable<T> task) throws T {
        long stamp = lock.writeLock();

        try {
            task.run();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public static <R, T extends Throwable> R supplyWithWriteLockT(StampedLock lock, ThrowingSupplier<R, T> task) throws T {
        long stamp = lock.writeLock();

        try {
            return task.get();
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}
