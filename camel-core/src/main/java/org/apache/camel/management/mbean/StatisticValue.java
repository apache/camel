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

public class StatisticValue extends Statistic {

    private final AtomicLong value = new AtomicLong(-1);

    public void updateValue(long newValue) {
        value.set(newValue);
    }

    public long getValue() {
        return value.get();
    }

    @Override
    public String toString() {
        return "" + value.get();
    }

    @Override
    public boolean isUpdated() {
        return value.get() != -1;
    }

    public void reset() {
        value.set(-1);
    }

}
