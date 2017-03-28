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

package org.apache.camel.component.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;

/**
 * This enumeration holds all values that may be used as the {@link WaitStrategy} used by producers on a Disruptor.
 * Blocking is the default {@link WaitStrategy}.
 */
public enum DisruptorWaitStrategy {
    /**
     * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
     * <p/>
     * This strategy can be used when throughput and low-latency are not as important as CPU resource.
     */
    Blocking(BlockingWaitStrategy.class),

    /**
     * Sleeping strategy that initially spins, then uses a Thread.yield(), and eventually for the minimum number of nanos
     * the OS and JVM will allow while the {@link com.lmax.disruptor.EventProcessor}s are waiting on a barrier.
     * <p/>
     * This strategy is a good compromise between performance and CPU resource. Latency spikes can occur after quiet periods.
     */
    Sleeping(SleepingWaitStrategy.class),

    /**
     * Busy Spin strategy that uses a busy spin loop for {@link com.lmax.disruptor.EventProcessor}s waiting on a barrier.
     * <p/>
     * This strategy will use CPU resource to avoid syscalls which can introduce latency jitter.  It is best
     * used when threads can be bound to specific CPU cores.
     */
    BusySpin(BusySpinWaitStrategy.class),

    /**
     * Yielding strategy that uses a Thread.yield() for {@link com.lmax.disruptor.EventProcessor}s waiting on a barrier
     * after an initially spinning.
     * <p/>
     * This strategy is a good compromise between performance and CPU resource without incurring significant latency spikes.
     */
    Yielding(YieldingWaitStrategy.class);

//    TODO PhasedBackoffWaitStrategy constructor requires parameters, unlike the other strategies. We leave it out for now
//    /**
//     * Phased wait strategy for waiting {@link EventProcessor}s on a barrier.<p/>
//     *
//     * This strategy can be used when throughput and low-latency are not as important as CPU resource.<\p></\p>
//     *
//     * Spins, then yields, then blocks on the configured BlockingStrategy.
//     */
//    PHASED_BACKOFF(PhasedBackoffWaitStrategy.class),

//    TODO TimeoutBlockingWaitStrategy constructor requires parameters, unlike the other strategies. We leave it out for now
//    /**
//     * TODO, wait for documentation from LMAX
//     */
//    TIMEOUT_BLOCKING(TimeoutBlockingWaitStrategy.class);

    private final Class<? extends WaitStrategy> waitStrategyClass;

    DisruptorWaitStrategy(final Class<? extends WaitStrategy> waitStrategyClass) {

        this.waitStrategyClass = waitStrategyClass;
    }

    public WaitStrategy createWaitStrategyInstance() throws Exception {
        return waitStrategyClass.getConstructor().newInstance();
    }
}
