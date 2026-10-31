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
package org.apache.camel.management.mbean;

import java.util.concurrent.atomic.LongAdder;

/**
 * A counter updated for every exchange by many threads, so it is striped: updates scale, and the value is summed when
 * it is read. A value read during concurrent updates can miss an update that is in progress.
 */
public class StatisticCounter extends Statistic {

    private final LongAdder value = new LongAdder();

    @Override
    public void updateValue(long newValue) {
        value.add(newValue);
    }

    @Override
    public long getValue() {
        return value.sum();
    }

    @Override
    public String toString() {
        return Long.toString(value.sum());
    }

    @Override
    public void reset() {
        value.reset();
    }

    @Override
    public boolean isUpdated() {
        // this is okay
        return true;
    }
}
