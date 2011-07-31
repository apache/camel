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
package org.apache.camel.management;

/**
 * Default implementation of {@link Statistic}
 */
public class DefaultStatistic implements Statistic {

    private final Statistic.UpdateMode updateMode;
    private long value;
    private long updateCount;

    /**
     * Instantiates a new statistic.
     *
     * @param updateMode The statistic update mode.
     */
    public DefaultStatistic(Statistic.UpdateMode updateMode) {
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

    public synchronized long getValue() {
        return this.value;
    }

    public synchronized long getUpdateCount() {
        return this.updateCount;
    }

    public synchronized void reset() {
        this.value = 0;
        this.updateCount = 0;
    }

    public String toString() {
        return "" + value;
    }

}
