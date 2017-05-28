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
package org.apache.camel.management.mbean;

import java.util.concurrent.atomic.AtomicLong;

public class StatisticMaximum extends Statistic {

    private final AtomicLong value = new AtomicLong(-1);

    public void updateValue(long newValue) {
        value.updateAndGet(value -> {
            if (value == -1 || value < newValue) {
                return newValue;
            } else {
                return value;
            }
        });
    }

    public long getValue() {
        long num = value.get();
        return num == -1 ? 0 : num;
    }

    @Override
    public String toString() {
        return "" + value.get();
    }

    public void reset() {
        value.set(-1);
    }

}
