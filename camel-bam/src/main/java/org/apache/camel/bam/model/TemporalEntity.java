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
package org.apache.camel.bam.model;

import static org.apache.camel.util.Time.millis;
import org.apache.camel.util.Time;

/**
 * @version $Revision: $
 */
public abstract class TemporalEntity extends EntitySupport{
    private long timeStarted;
    private long timeCompleted;
    private boolean completed;

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public long getTimeCompleted() {
        return timeCompleted;
    }

    public void setTimeCompleted(long timeCompleted) {
        this.timeCompleted = timeCompleted;
    }

    public long getTimeStarted() {
        return timeStarted;
    }

    public void setTimeStarted(long timeStarted) {
        this.timeStarted = timeStarted;
    }

    public Time getStartTime() {
        long value = getTimeStarted();
        if (value > 0) {
            return millis(value);
        }
        return null;
    }

    public Time getCompleteTime() {
        long value = getTimeCompleted();
        if (value > 0) {
            return millis(value);
        }
        return null;
    }
}
