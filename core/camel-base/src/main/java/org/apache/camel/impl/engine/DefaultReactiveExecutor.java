/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.impl.engine;

import org.apache.camel.AsyncCallback;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.support.ReactiveHelper;

/**
 * Default {@link ReactiveExecutor}.
 */
public class DefaultReactiveExecutor implements ReactiveExecutor {

    // TODO: ReactiveHelper code should be moved here and not static
    // ppl should use the SPI interface

    @Override
    public void scheduleMain(Runnable runnable) {
        ReactiveHelper.scheduleMain(runnable);
    }

    @Override
    public void scheduleSync(Runnable runnable) {
        ReactiveHelper.scheduleSync(runnable);
    }

    @Override
    public void scheduleMain(Runnable runnable, String description) {
        ReactiveHelper.scheduleMain(runnable, description);
    }

    @Override
    public void schedule(Runnable runnable) {
        ReactiveHelper.schedule(runnable);
    }

    @Override
    public void schedule(Runnable runnable, String description) {
        ReactiveHelper.schedule(runnable, description);
    }

    @Override
    public void scheduleSync(Runnable runnable, String description) {
        ReactiveHelper.scheduleSync(runnable, description);
    }

    @Override
    public boolean executeFromQueue() {
        return ReactiveHelper.executeFromQueue();
    }

    @Override
    public void callback(AsyncCallback callback) {
        ReactiveHelper.callback(callback);
    }
}
