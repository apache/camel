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
import java.util.concurrent.atomic.LongAdder;

/**
 * Default implementation of {@link Statistic}
 */
public class Statistic {

    /**
     * Statistics mode
     * <ul>
     * <li>VALUE - A statistic with this update mode is a simple value that is a straight forward
     * representation of the updated value.</li>
     * <li>DELTA - A statistic with this update mode is a value that represents the delta
     * between the last two recorded values (or the initial value if two updates have
     * not been recorded). This value can be negative if the delta goes up or down.</li>
     * <li>COUNTER - A statistic with this update mode interprets updates as increments (positive values)
     * or decrements (negative values) to the current value.</li>
     * <li>MAXIMUM - A statistic with this update mode is a value that represents the maximum value
     * amongst the update values applied to this statistic.</li>
     * <li>MINIMUM - A statistic with this update mode is a value that represents the minimum value
     * amongst the update values applied to this statistic.</li>
     * <ul>
     */
    public enum UpdateMode {
        VALUE, DELTA, COUNTER, MAXIMUM, MINIMUM
    }

    private final UpdateMode updateMode;
    private final AtomicLong value = new AtomicLong();
    private final AtomicLong lastValue;
    private final LongAdder updateCount = new LongAdder();

    /**
     * Instantiates a new statistic.
     *
     * @param name  name of statistic
     * @param owner owner
     * @param updateMode The statistic update mode.
     */
    public Statistic(String name, Object owner, UpdateMode updateMode) {
        this.updateMode = updateMode;
        if (UpdateMode.DELTA == updateMode) {
            this.lastValue = new AtomicLong();
        } else {
            this.lastValue = null;
        }
    }

    public void updateValue(long newValue) {
        switch (updateMode) {
        case COUNTER:
            value.addAndGet(newValue);
            break;
        case VALUE:
            value.set(newValue);
            break;
        case DELTA:
            if (updateCount.longValue() > 0) {
                // remember previous value before updating it
                lastValue.set(value.longValue());
            }
            value.set(newValue);
            break;
        case MAXIMUM:
            value.updateAndGet(value -> {
                if (updateCount.longValue() == 0 || value < newValue) {
                    return newValue;
                } else {
                    return value;
                }
            });
            break;
        case MINIMUM:
            value.updateAndGet(value -> {
                if (updateCount.longValue() == 0 || value > newValue) {
                    return newValue;
                } else {
                    return value;
                }
            });
            break;
        default:
        }
        updateCount.add(1);
    }

    public void increment() {
        updateValue(1);
    }

    public void decrement() {
        updateValue(-1);
    }

    public long getValue() {
        if (updateMode == UpdateMode.DELTA) {
            if (updateCount.longValue() == 0) {
                return value.get();
            } else {
                return value.get() - lastValue.get();
            }
        }
        return value.get();
    }

    public long getUpdateCount() {
        return updateCount.longValue();
    }

    public void reset() {
        value.set(0);
        if (lastValue != null) {
            lastValue.set(0);
        }
        updateCount.reset();
    }

    public String toString() {
        return "" + value.get();
    }

}
