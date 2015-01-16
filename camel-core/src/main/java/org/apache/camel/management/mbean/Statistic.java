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

/**
 * Default implementation of {@link Statistic}
 */
public class Statistic {

    /**
     * Statistics mode
     * <ul>
     * <li>VALUE - A statistic with this update mode is a simple value that is a straight forward
     * representation of the updated value.</li>
     * <li>DIFFERENCE - A statistic with this update mode is a value that represents the difference
     * between the last two recorded values (or the initial value if two updates have
     * not been recorded).</li>
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
        VALUE, DIFFERENCE, DELTA, COUNTER, MAXIMUM, MINIMUM
    }

    private final UpdateMode updateMode;
    private long lastValue;
    private long value;
    private long updateCount;

    /**
     * Instantiates a new statistic.
     *
     * @param name  name of statistic
     * @param owner owner
     * @param updateMode The statistic update mode.
     */
    public Statistic(String name, Object owner, UpdateMode updateMode) {
        this.updateMode = updateMode;
    }

    public synchronized void updateValue(long newValue) {
        switch (this.updateMode) {
        case COUNTER:
            this.value += newValue;
            break;
        case VALUE:
            this.value = newValue;
            break;
        case DIFFERENCE:
            this.value -= newValue;
            if (this.value < 0) {
                this.value = -this.value;
            }
            break;
        case DELTA:
            if (updateCount > 0) {
                this.lastValue = this.value;
            }
            this.value = newValue;
            break;
        case MAXIMUM:
            // initialize value at first time
            if (this.updateCount == 0 || this.value < newValue) {
                this.value = newValue;
            }
            break;
        case MINIMUM:
            // initialize value at first time
            if (this.updateCount == 0 || this.value > newValue) {
                this.value = newValue;
            }
            break;
        default:
        }
        this.updateCount++;
    }

    public synchronized void increment() {
        updateValue(1);
    }

    public synchronized void decrement() {
        updateValue(-1);
    }

    public synchronized long getValue() {
        if (updateMode == UpdateMode.DELTA) {
            if (updateCount == 0) {
                return this.value;
            } else {
                return this.value - this.lastValue;
            }
        } else {
            return this.value;
        }
    }

    public synchronized long getUpdateCount() {
        return this.updateCount;
    }

    public synchronized void reset() {
        this.value = 0;
        this.lastValue = 0;
        this.updateCount = 0;
    }

    public String toString() {
        return "" + value;
    }

}
