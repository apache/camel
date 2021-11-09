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

import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TaskTestSupport {
    protected final int maxIterations = 5;
    protected volatile int taskCount;

    protected boolean booleanSupplier() {
        taskCount++;

        return false;
    }

    protected boolean taskPredicate(Object o) {
        assertNotNull(o);
        taskCount++;

        return false;
    }

    protected boolean taskPredicateWithDeterministicStop(Integer stopAtValue) {
        assertNotNull(stopAtValue);

        taskCount++;
        if (taskCount == stopAtValue.intValue()) {
            return true;
        }

        return false;
    }

    protected boolean taskPredicateWithDeterministicStopSlow(Integer stopAtValue) {
        assertNotNull(stopAtValue);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            return false;
        }

        taskCount++;
        if (taskCount == stopAtValue.intValue()) {
            return true;
        }

        return false;
    }

    @BeforeEach
    void setUp() {
        taskCount = 0;
    }
}
