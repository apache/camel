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
package org.apache.camel.impl.health;

import java.util.Map;

import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.health.HealthCheckResultBuilder;

public final class RoutePerformanceCounterEvaluators {

    private RoutePerformanceCounterEvaluators() {
    }

    // ********************************
    // Helpers
    // ********************************

    public static PerformanceCounterEvaluator<ManagedRouteMBean> exchangesFailed(long threshold) {
        return new ExchangesFailed(threshold);
    }

    public static PerformanceCounterEvaluator<ManagedRouteMBean> exchangesInflight(long threshold) {
        return new ExchangesInflight(threshold);
    }

    public static PerformanceCounterEvaluator<ManagedRouteMBean> redeliveries(long threshold) {
        return new Redeliveries(threshold);
    }

    public static PerformanceCounterEvaluator<ManagedRouteMBean> externalRedeliveries(long threshold) {
        return new ExternalRedeliveries(threshold);
    }

    public static PerformanceCounterEvaluator<ManagedRouteMBean> lastProcessingTime(long timeThreshold, int failuresThreshold) {
        return new LastProcessingTime(timeThreshold, failuresThreshold);
    }

    public static PerformanceCounterEvaluator<ManagedRouteMBean> minProcessingTime(long timeThreshold, int failuresThreshold) {
        return new MinProcessingTime(timeThreshold, failuresThreshold);
    }

    public static PerformanceCounterEvaluator<ManagedRouteMBean> meanProcessingTime(long timeThreshold, int failuresThreshold) {
        return new MeanProcessingTime(timeThreshold, failuresThreshold);
    }

    public static PerformanceCounterEvaluator<ManagedRouteMBean> maxProcessingTime(long timeThreshold, int failuresThreshold) {
        return new MaxProcessingTime(timeThreshold, failuresThreshold);
    }

    // ********************************
    // Impls
    // ********************************

    public static final class ExchangesFailed implements PerformanceCounterEvaluator<ManagedRouteMBean> {
        private final long threshold;

        public ExchangesFailed(long threshold) {
            this.threshold = threshold;
        }

        @Override
        public void test(ManagedRouteMBean counter, HealthCheckResultBuilder builder, Map<String, Object> options) {
            try {
                long value = counter.getExchangesFailed();
                if (value > threshold) {
                    builder.down();
                }

                builder.detail("exchanges.failed", value);
                builder.detail("exchanges.failed.threshold", threshold);
            } catch (Exception e) {
            }
        }
    }

    public static final class ExchangesInflight implements PerformanceCounterEvaluator<ManagedRouteMBean> {
        private final long threshold;

        public ExchangesInflight(long threshold) {
            this.threshold = threshold;
        }

        @Override
        public void test(ManagedRouteMBean counter, HealthCheckResultBuilder builder, Map<String, Object> options) {
            try {
                long value = counter.getExchangesInflight();
                if (value > threshold) {
                    builder.down();
                }

                builder.detail("exchanges.inflight", value);
                builder.detail("exchanges.inflight.threshold", threshold);
            } catch (Exception e) {
            }
        }
    }

    public static final class Redeliveries implements PerformanceCounterEvaluator<ManagedRouteMBean> {
        private final long threshold;

        public Redeliveries(long threshold) {
            this.threshold = threshold;
        }

        @Override
        public void test(ManagedRouteMBean counter, HealthCheckResultBuilder builder, Map<String, Object> options) {
            try {
                long value = counter.getRedeliveries();
                if (value > threshold) {
                    builder.down();
                }

                builder.detail("exchanges.redeliveries", value);
                builder.detail("exchanges.redeliveries.threshold", threshold);
            } catch (Exception e) {
            }
        }
    }

    public static final class ExternalRedeliveries implements PerformanceCounterEvaluator<ManagedRouteMBean> {
        private final long threshold;

        public ExternalRedeliveries(long threshold) {
            this.threshold = threshold;
        }

        @Override
        public void test(ManagedRouteMBean counter, HealthCheckResultBuilder builder, Map<String, Object> options) {
            try {
                long value = counter.getExternalRedeliveries();
                if (value > threshold) {
                    builder.down();
                }

                builder.detail("exchanges.external-redeliveries", value);
                builder.detail("exchanges.external-redeliveries.threshold", threshold);
            } catch (Exception e) {
            }
        }
    }

    public static final class LastProcessingTime implements PerformanceCounterEvaluator<ManagedRouteMBean> {
        private final long timeThreshold;
        private final int failuresThreshold;

        private volatile int failureCount;

        public LastProcessingTime(long timeThreshold, int failuresThreshold) {
            this.timeThreshold = timeThreshold;
            this.failuresThreshold = failuresThreshold;
        }

        @Override
        public void test(ManagedRouteMBean counter, HealthCheckResultBuilder builder, Map<String, Object> options) {
            try {
                long value = counter.getLastProcessingTime();
                if (value > timeThreshold) {
                    failureCount++;

                    if (failureCount > failuresThreshold) {
                        builder.down();
                    }
                } else {
                    failureCount = 0;
                }

                builder.detail("exchanges.last-processing-time", value);
                builder.detail("exchanges.last-processing-time.threshold.time", timeThreshold);
                builder.detail("exchanges.last-processing-time.threshold.failures", failuresThreshold);
            } catch (Exception e) {
            }
        }
    }

    public static final class MinProcessingTime implements PerformanceCounterEvaluator<ManagedRouteMBean> {
        private final long timeThreshold;
        private final int failuresThreshold;

        private volatile int failureCount;

        public MinProcessingTime(long timeThreshold, int failuresThreshold) {
            this.timeThreshold = timeThreshold;
            this.failuresThreshold = failuresThreshold;
        }

        @Override
        public void test(ManagedRouteMBean counter, HealthCheckResultBuilder builder, Map<String, Object> options) {
            try {
                long value = counter.getMinProcessingTime();
                if (value > timeThreshold) {
                    failureCount++;

                    if (failureCount > failuresThreshold) {
                        builder.down();
                    }
                } else {
                    failureCount = 0;
                }

                builder.detail("exchanges.min-processing-time", value);
                builder.detail("exchanges.min-processing-time.threshold.time", timeThreshold);
                builder.detail("exchanges.min-processing-time.threshold.failures", failuresThreshold);
            } catch (Exception e) {
            }
        }
    }

    public static final class MeanProcessingTime implements PerformanceCounterEvaluator<ManagedRouteMBean> {
        private final long timeThreshold;
        private final int failuresThreshold;

        private volatile int failureCount;

        public MeanProcessingTime(long timeThreshold, int failuresThreshold) {
            this.timeThreshold = timeThreshold;
            this.failuresThreshold = failuresThreshold;
        }

        @Override
        public void test(ManagedRouteMBean counter, HealthCheckResultBuilder builder, Map<String, Object> options) {
            try {
                long value = counter.getMeanProcessingTime();
                if (value > timeThreshold) {
                    failureCount++;

                    if (failureCount > failuresThreshold) {
                        builder.down();
                    }
                } else {
                    failureCount = 0;
                }

                builder.detail("exchanges.mean-processing-time", value);
                builder.detail("exchanges.mean-processing-time.threshold.time", timeThreshold);
                builder.detail("exchanges.mean-processing-time.threshold.failures", failuresThreshold);
            } catch (Exception e) {
            }
        }
    }

    public static final class MaxProcessingTime implements PerformanceCounterEvaluator<ManagedRouteMBean> {
        private final long timeThreshold;
        private final int failuresThreshold;

        private volatile int failureCount;

        public MaxProcessingTime(long timeThreshold, int failuresThreshold) {
            this.timeThreshold = timeThreshold;
            this.failuresThreshold = failuresThreshold;
        }

        @Override
        public void test(ManagedRouteMBean counter, HealthCheckResultBuilder builder, Map<String, Object> options) {
            try {
                long value = counter.getMaxProcessingTime();
                if (value > timeThreshold) {
                    failureCount++;

                    if (failureCount > failuresThreshold) {
                        builder.down();
                    }
                } else {
                    failureCount = 0;
                }

                builder.detail("exchanges.max-processing-time", value);
                builder.detail("exchanges.max-processing-time.threshold.time", timeThreshold);
                builder.detail("exchanges.max-processing-time.threshold.failures", failuresThreshold);
            } catch (Exception e) {
            }
        }
    }
}
