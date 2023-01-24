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

package org.apache.camel.support.task;

import java.util.function.Supplier;

/**
 * A helper class for building tasks
 */
public final class Tasks {

    private Tasks() {

    }

    /**
     * Creates a new background task builder
     *
     * @return an instance of a background task builder
     */
    public static BackgroundTask.BackgroundTaskBuilder backgroundTask() {
        return task(BackgroundTask.BackgroundTaskBuilder::new);
    }

    /**
     * Creates a new foreground task builder
     *
     * @return an instance of a foreground task builder
     */
    public static ForegroundTask.ForegroundTaskBuilder foregroundTask() {
        return task(ForegroundTask.ForegroundTaskBuilder::new);
    }

    /**
     * A generic builder for task builders
     *
     * @param  taskBuilderSupplier A supplier of tasks (usually a parameterless constructor in the form of Builder::new)
     * @param  <T>                 the type of tasks that the builder builds
     * @param  <Y>                 the type of the task builder to provide
     * @return                     A new instance of the given task builder
     */
    public static <T extends BlockingTask, Y extends TaskBuilder<T>> Y task(Supplier<Y> taskBuilderSupplier) {
        return taskBuilderSupplier.get();
    }
}
