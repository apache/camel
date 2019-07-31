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
package org.apache.camel.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * A {@link Rejectable} {@link FutureTask} used by {@link RejectableThreadPoolExecutor}.
 *
 * @see RejectableThreadPoolExecutor
 */
public class RejectableFutureTask<V> extends FutureTask<V> implements Rejectable {

    private final Rejectable rejectable;

    public RejectableFutureTask(Callable<V> callable) {
        super(callable);
        this.rejectable = callable instanceof Rejectable ? (Rejectable) callable : null;
    }

    public RejectableFutureTask(Runnable runnable, V result) {
        super(runnable, result);
        this.rejectable = runnable instanceof Rejectable ? (Rejectable) runnable : null;
    }

    @Override
    public void reject() {
        if (rejectable != null) {
            rejectable.reject();
        }
    }

}
