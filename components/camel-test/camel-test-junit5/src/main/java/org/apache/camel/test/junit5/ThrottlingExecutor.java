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

package org.apache.camel.test.junit5;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

public final class ThrottlingExecutor {

    private List<IntConsumer> beforeTasks = new ArrayList<>();
    private List<IntConsumer> afterTasks = new ArrayList<>();
    private int repetitions;
    private long beforeWait;
    private long awaiting;
    private TimeUnit timeUnit;

    private ThrottlingExecutor() {

    }

    public static ThrottlingExecutor slowly() {
        return new ThrottlingExecutor();
    }

    public ThrottlingExecutor repeat(int repetitions) {
        this.repetitions = repetitions;

        return this;
    }

    public ThrottlingExecutor beforeWait() throws InterruptedException {
        Thread.sleep(timeUnit.toMillis(awaiting));

        return this;
    }

    public ThrottlingExecutor awaiting(long awaiting, TimeUnit timeUnit) {
        this.awaiting = awaiting;
        this.timeUnit = timeUnit;

        return this;
    }

    public ThrottlingExecutor beforeEach(IntConsumer beforeTask) {
        beforeTasks.add(beforeTask);

        return this;
    }

    public ThrottlingExecutor afterEach(IntConsumer afterTask) {
        afterTasks.add(afterTask);

        return this;
    }

    private static void runTasks(List<IntConsumer> taskList, Integer index) {
        for (IntConsumer consumer : taskList) {
            consumer.accept(index);
        }
    }

    public void execute() throws InterruptedException {
        for (int i = 0; i < repetitions; i++) {
            runTasks(beforeTasks, i);

            Thread.sleep(timeUnit.toMillis(awaiting));

            runTasks(afterTasks, i);
        }

    }
}
