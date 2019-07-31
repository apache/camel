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
package org.apache.camel.component.sjms.taskmanager;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread safe task manager that allows you to add and cancel
 * {@link TimerTask} objects.
 */
public class TimedTaskManager {

    private final Timer timer = new Timer();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public TimedTaskManager() {
    }

    public void addTask(TimerTask task, long delay) {
        try {
            lock.writeLock().lock();
            timer.schedule(task, delay);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void cancelTasks() {
        try {
            lock.writeLock().lock();
            timer.cancel();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
