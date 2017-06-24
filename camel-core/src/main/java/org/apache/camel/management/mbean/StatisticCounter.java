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
package org.apache.camel.management.mbean;

import java.util.concurrent.atomic.AtomicLong;

public class StatisticCounter extends Statistic {

    private final AtomicLong value = new AtomicLong(0);

    public void updateValue(long newValue) {
        value.getAndAdd(newValue);
    }

    public long getValue() {
        return value.get();
    }

    @Override
    public String toString() {
        return "" + value.get();
    }

    public void reset() {
        value.set(0);
    }

    @Override
    public boolean isUpdated() {
        // this is okay
        return true;
    }
}
