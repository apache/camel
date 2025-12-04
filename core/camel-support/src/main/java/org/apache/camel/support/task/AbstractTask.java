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

abstract class AbstractTask implements Task {

    static final long NEVER = -1L;

    final String name;
    Status status;
    long firstAttemptTime;
    long lastAttemptTime;
    long nextAttemptTime;
    Throwable cause;

    public AbstractTask(String name) {
        this.name = name;
        this.status = Status.Active;
        this.firstAttemptTime = NEVER;
        this.lastAttemptTime = NEVER;
        this.nextAttemptTime = NEVER;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public long getFirstAttemptTime() {
        return firstAttemptTime;
    }

    @Override
    public long getLastAttemptTime() {
        return lastAttemptTime;
    }

    @Override
    public long getNextAttemptTime() {
        return nextAttemptTime;
    }

    @Override
    public long getCurrentElapsedTime() {
        if (firstAttemptTime > 0) {
            return System.currentTimeMillis() - firstAttemptTime;
        }
        return NEVER;
    }

    @Override
    public Throwable getException() {
        return cause;
    }
}
