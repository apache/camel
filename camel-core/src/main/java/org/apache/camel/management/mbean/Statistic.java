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
 * Base implementation of {@link Statistic}
 * <p/>
 * The following modes is available:
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
public abstract class Statistic {

    public abstract void updateValue(long newValue);

    public void increment() {
        updateValue(1);
    }

    public void decrement() {
        updateValue(-1);
    }

    public abstract long getValue();

    @Deprecated
    public long getUpdateCount() {
        return 0;
    }

    public abstract void reset();

}
