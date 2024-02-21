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
package org.apache.camel.impl.engine;

import org.apache.camel.CamelContextAware;
import org.apache.camel.TimerListener;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.support.TimerListenerManager;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StopWatch;

/**
 * A {@link PeriodTaskScheduler} that schedules generic tasks from custom components that are defined with the
 * {@link org.apache.camel.spi.annotations.PeriodicTask} annotation.
 */
public final class DefaultPeriodTaskScheduler extends TimerListenerManager implements PeriodTaskScheduler {

    @Override
    public void schedulePeriodTask(Runnable task, long period) {
        addTimerListener(new TaskWrapper(task, period));
    }

    @Override
    public <T> T getTaskByType(Class<T> type) {
        for (TimerListener listener : getListeners()) {
            Object task = listener;
            if (listener instanceof TaskWrapper) {
                task = ((TaskWrapper) listener).getTask();
            }
            if (type.isInstance(task)) {
                return type.cast(task);
            }
        }
        return null;
    }

    @Override
    public void addTimerListener(TimerListener listener) {
        if (listener instanceof TaskWrapper) {
            super.addTimerListener(listener);
        } else {
            throw new IllegalArgumentException("Use the addPeriodTask method");
        }
    }

    private final class TaskWrapper extends ServiceSupport implements TimerListener {

        private final StopWatch watch = new StopWatch();
        private final Runnable task;
        private final long period;

        public TaskWrapper(Runnable task, long period) {
            this.task = task;
            this.period = period;
        }

        public Runnable getTask() {
            return task;
        }

        @Override
        public void onTimer() {
            if (watch.taken() > period) {
                watch.restart();
                task.run();
            }
        }

        @Override
        protected void doBuild() throws Exception {
            CamelContextAware.trySetCamelContext(task, getCamelContext());
            ServiceHelper.buildService(task);
        }

        @Override
        protected void doInit() throws Exception {
            ServiceHelper.initService(task);
        }

        @Override
        protected void doStart() throws Exception {
            ServiceHelper.startService(task);
        }

        @Override
        protected void doStop() throws Exception {
            ServiceHelper.stopService(task);
        }

        @Override
        protected void doShutdown() throws Exception {
            ServiceHelper.stopAndShutdownService(task);
        }

        @Override
        public String toString() {
            return task.toString();
        }
    }
}
