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
package org.apache.camel.component.microprofile.faulttolerance;

import java.util.concurrent.ExecutorService;

import io.smallrye.faulttolerance.core.timer.ThreadTimer;
import io.smallrye.faulttolerance.core.timer.Timer;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Service to manage the lifecycle of the SmallRye Fault Tolerance Timer. Primarily used when running without CDI
 * container support.
 */
public class FaultToleranceTimerService extends ServiceSupport implements CamelContextAware {
    private ExecutorService threadTimerExecutorService;
    private Timer timer;
    private CamelContext camelContext;

    @Override
    protected void doInit() throws Exception {
        threadTimerExecutorService
                = getCamelContext().getExecutorServiceManager().newCachedThreadPool(this, "CircuitBreakerThreadTimer");
        timer = new ThreadTimer(threadTimerExecutorService);
    }

    @Override
    protected void doStop() throws Exception {
        if (timer != null) {
            try {
                timer.shutdown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                timer = null;
            }
        }

        if (threadTimerExecutorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(threadTimerExecutorService);
            threadTimerExecutorService = null;
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    public Timer getTimer() {
        return timer;
    }
}
