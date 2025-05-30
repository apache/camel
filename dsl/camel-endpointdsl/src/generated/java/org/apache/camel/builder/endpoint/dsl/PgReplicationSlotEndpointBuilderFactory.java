/* Generated by camel build tools - do NOT edit this file! */
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
package org.apache.camel.builder.endpoint.dsl;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;
import javax.annotation.processing.Generated;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.builder.endpoint.AbstractEndpointBuilder;

/**
 * Poll for PostgreSQL Write-Ahead Log (WAL) records using Streaming Replication
 * Slots.
 * 
 * Generated by camel build tools - do NOT edit this file!
 */
@Generated("org.apache.camel.maven.packaging.EndpointDslMojo")
public interface PgReplicationSlotEndpointBuilderFactory {

    /**
     * Builder for endpoint for the PostgresSQL Replication Slot component.
     */
    public interface PgReplicationSlotEndpointBuilder
            extends
                EndpointConsumerBuilder {
        default AdvancedPgReplicationSlotEndpointBuilder advanced() {
            return (AdvancedPgReplicationSlotEndpointBuilder) this;
        }

        /**
         * Postgres password.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Group: common
         * 
         * @param password the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder password(String password) {
            doSetProperty("password", password);
            return this;
        }
        /**
         * Postgres user.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Default: postgres
         * Group: common
         * 
         * @param user the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder user(String user) {
            doSetProperty("user", user);
            return this;
        }
        /**
         * If the polling consumer did not poll any files, you can enable this
         * option to send an empty message (no body) instead.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: false
         * Group: consumer
         * 
         * @param sendEmptyMessageWhenIdle the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder sendEmptyMessageWhenIdle(boolean sendEmptyMessageWhenIdle) {
            doSetProperty("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle);
            return this;
        }
        /**
         * If the polling consumer did not poll any files, you can enable this
         * option to send an empty message (no body) instead.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: false
         * Group: consumer
         * 
         * @param sendEmptyMessageWhenIdle the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder sendEmptyMessageWhenIdle(String sendEmptyMessageWhenIdle) {
            doSetProperty("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle);
            return this;
        }
        /**
         * The number of subsequent error polls (failed due some error) that
         * should happen before the backoffMultipler should kick-in.
         * 
         * The option is a: <code>int</code> type.
         * 
         * Group: scheduler
         * 
         * @param backoffErrorThreshold the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder backoffErrorThreshold(int backoffErrorThreshold) {
            doSetProperty("backoffErrorThreshold", backoffErrorThreshold);
            return this;
        }
        /**
         * The number of subsequent error polls (failed due some error) that
         * should happen before the backoffMultipler should kick-in.
         * 
         * The option will be converted to a <code>int</code> type.
         * 
         * Group: scheduler
         * 
         * @param backoffErrorThreshold the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder backoffErrorThreshold(String backoffErrorThreshold) {
            doSetProperty("backoffErrorThreshold", backoffErrorThreshold);
            return this;
        }
        /**
         * The number of subsequent idle polls that should happen before the
         * backoffMultipler should kick-in.
         * 
         * The option is a: <code>int</code> type.
         * 
         * Group: scheduler
         * 
         * @param backoffIdleThreshold the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder backoffIdleThreshold(int backoffIdleThreshold) {
            doSetProperty("backoffIdleThreshold", backoffIdleThreshold);
            return this;
        }
        /**
         * The number of subsequent idle polls that should happen before the
         * backoffMultipler should kick-in.
         * 
         * The option will be converted to a <code>int</code> type.
         * 
         * Group: scheduler
         * 
         * @param backoffIdleThreshold the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder backoffIdleThreshold(String backoffIdleThreshold) {
            doSetProperty("backoffIdleThreshold", backoffIdleThreshold);
            return this;
        }
        /**
         * To let the scheduled polling consumer backoff if there has been a
         * number of subsequent idles/errors in a row. The multiplier is then
         * the number of polls that will be skipped before the next actual
         * attempt is happening again. When this option is in use then
         * backoffIdleThreshold and/or backoffErrorThreshold must also be
         * configured.
         * 
         * The option is a: <code>int</code> type.
         * 
         * Group: scheduler
         * 
         * @param backoffMultiplier the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder backoffMultiplier(int backoffMultiplier) {
            doSetProperty("backoffMultiplier", backoffMultiplier);
            return this;
        }
        /**
         * To let the scheduled polling consumer backoff if there has been a
         * number of subsequent idles/errors in a row. The multiplier is then
         * the number of polls that will be skipped before the next actual
         * attempt is happening again. When this option is in use then
         * backoffIdleThreshold and/or backoffErrorThreshold must also be
         * configured.
         * 
         * The option will be converted to a <code>int</code> type.
         * 
         * Group: scheduler
         * 
         * @param backoffMultiplier the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder backoffMultiplier(String backoffMultiplier) {
            doSetProperty("backoffMultiplier", backoffMultiplier);
            return this;
        }
        /**
         * Milliseconds before the next poll.
         * 
         * The option is a: <code>long</code> type.
         * 
         * Default: 500
         * Group: scheduler
         * 
         * @param delay the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder delay(long delay) {
            doSetProperty("delay", delay);
            return this;
        }
        /**
         * Milliseconds before the next poll.
         * 
         * The option will be converted to a <code>long</code> type.
         * 
         * Default: 500
         * Group: scheduler
         * 
         * @param delay the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder delay(String delay) {
            doSetProperty("delay", delay);
            return this;
        }
        /**
         * If greedy is enabled, then the ScheduledPollConsumer will run
         * immediately again, if the previous run polled 1 or more messages.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: false
         * Group: scheduler
         * 
         * @param greedy the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder greedy(boolean greedy) {
            doSetProperty("greedy", greedy);
            return this;
        }
        /**
         * If greedy is enabled, then the ScheduledPollConsumer will run
         * immediately again, if the previous run polled 1 or more messages.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: false
         * Group: scheduler
         * 
         * @param greedy the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder greedy(String greedy) {
            doSetProperty("greedy", greedy);
            return this;
        }
        /**
         * Milliseconds before the first poll starts.
         * 
         * The option is a: <code>long</code> type.
         * 
         * Default: 1000
         * Group: scheduler
         * 
         * @param initialDelay the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder initialDelay(long initialDelay) {
            doSetProperty("initialDelay", initialDelay);
            return this;
        }
        /**
         * Milliseconds before the first poll starts.
         * 
         * The option will be converted to a <code>long</code> type.
         * 
         * Default: 1000
         * Group: scheduler
         * 
         * @param initialDelay the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder initialDelay(String initialDelay) {
            doSetProperty("initialDelay", initialDelay);
            return this;
        }
        /**
         * Specifies a maximum limit of number of fires. So if you set it to 1,
         * the scheduler will only fire once. If you set it to 5, it will only
         * fire five times. A value of zero or negative means fire forever.
         * 
         * The option is a: <code>long</code> type.
         * 
         * Default: 0
         * Group: scheduler
         * 
         * @param repeatCount the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder repeatCount(long repeatCount) {
            doSetProperty("repeatCount", repeatCount);
            return this;
        }
        /**
         * Specifies a maximum limit of number of fires. So if you set it to 1,
         * the scheduler will only fire once. If you set it to 5, it will only
         * fire five times. A value of zero or negative means fire forever.
         * 
         * The option will be converted to a <code>long</code> type.
         * 
         * Default: 0
         * Group: scheduler
         * 
         * @param repeatCount the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder repeatCount(String repeatCount) {
            doSetProperty("repeatCount", repeatCount);
            return this;
        }
        /**
         * The consumer logs a start/complete log line when it polls. This
         * option allows you to configure the logging level for that.
         * 
         * The option is a: <code>org.apache.camel.LoggingLevel</code> type.
         * 
         * Default: TRACE
         * Group: scheduler
         * 
         * @param runLoggingLevel the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder runLoggingLevel(org.apache.camel.LoggingLevel runLoggingLevel) {
            doSetProperty("runLoggingLevel", runLoggingLevel);
            return this;
        }
        /**
         * The consumer logs a start/complete log line when it polls. This
         * option allows you to configure the logging level for that.
         * 
         * The option will be converted to a
         * <code>org.apache.camel.LoggingLevel</code> type.
         * 
         * Default: TRACE
         * Group: scheduler
         * 
         * @param runLoggingLevel the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder runLoggingLevel(String runLoggingLevel) {
            doSetProperty("runLoggingLevel", runLoggingLevel);
            return this;
        }
        /**
         * Allows for configuring a custom/shared thread pool to use for the
         * consumer. By default each consumer has its own single threaded thread
         * pool.
         * 
         * The option is a:
         * <code>java.util.concurrent.ScheduledExecutorService</code> type.
         * 
         * Group: scheduler
         * 
         * @param scheduledExecutorService the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
            doSetProperty("scheduledExecutorService", scheduledExecutorService);
            return this;
        }
        /**
         * Allows for configuring a custom/shared thread pool to use for the
         * consumer. By default each consumer has its own single threaded thread
         * pool.
         * 
         * The option will be converted to a
         * <code>java.util.concurrent.ScheduledExecutorService</code> type.
         * 
         * Group: scheduler
         * 
         * @param scheduledExecutorService the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder scheduledExecutorService(String scheduledExecutorService) {
            doSetProperty("scheduledExecutorService", scheduledExecutorService);
            return this;
        }
        /**
         * To use a cron scheduler from either camel-spring or camel-quartz
         * component. Use value spring or quartz for built in scheduler.
         * 
         * The option is a: <code>java.lang.Object</code> type.
         * 
         * Default: none
         * Group: scheduler
         * 
         * @param scheduler the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder scheduler(Object scheduler) {
            doSetProperty("scheduler", scheduler);
            return this;
        }
        /**
         * To use a cron scheduler from either camel-spring or camel-quartz
         * component. Use value spring or quartz for built in scheduler.
         * 
         * The option will be converted to a <code>java.lang.Object</code> type.
         * 
         * Default: none
         * Group: scheduler
         * 
         * @param scheduler the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder scheduler(String scheduler) {
            doSetProperty("scheduler", scheduler);
            return this;
        }
        /**
         * To configure additional properties when using a custom scheduler or
         * any of the Quartz, Spring based scheduler. This is a multi-value
         * option with prefix: scheduler.
         * 
         * The option is a: <code>java.util.Map&lt;java.lang.String,
         * java.lang.Object&gt;</code> type.
         * The option is multivalued, and you can use the
         * schedulerProperties(String, Object) method to add a value (call the
         * method multiple times to set more values).
         * 
         * Group: scheduler
         * 
         * @param key the option key
         * @param value the option value
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder schedulerProperties(String key, Object value) {
            doSetMultiValueProperty("schedulerProperties", "scheduler." + key, value);
            return this;
        }
        /**
         * To configure additional properties when using a custom scheduler or
         * any of the Quartz, Spring based scheduler. This is a multi-value
         * option with prefix: scheduler.
         * 
         * The option is a: <code>java.util.Map&lt;java.lang.String,
         * java.lang.Object&gt;</code> type.
         * The option is multivalued, and you can use the
         * schedulerProperties(String, Object) method to add a value (call the
         * method multiple times to set more values).
         * 
         * Group: scheduler
         * 
         * @param values the values
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder schedulerProperties(Map values) {
            doSetMultiValueProperties("schedulerProperties", "scheduler.", values);
            return this;
        }
        /**
         * Whether the scheduler should be auto started.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: true
         * Group: scheduler
         * 
         * @param startScheduler the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder startScheduler(boolean startScheduler) {
            doSetProperty("startScheduler", startScheduler);
            return this;
        }
        /**
         * Whether the scheduler should be auto started.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: true
         * Group: scheduler
         * 
         * @param startScheduler the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder startScheduler(String startScheduler) {
            doSetProperty("startScheduler", startScheduler);
            return this;
        }
        /**
         * Time unit for initialDelay and delay options.
         * 
         * The option is a: <code>java.util.concurrent.TimeUnit</code> type.
         * 
         * Default: MILLISECONDS
         * Group: scheduler
         * 
         * @param timeUnit the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder timeUnit(TimeUnit timeUnit) {
            doSetProperty("timeUnit", timeUnit);
            return this;
        }
        /**
         * Time unit for initialDelay and delay options.
         * 
         * The option will be converted to a
         * <code>java.util.concurrent.TimeUnit</code> type.
         * 
         * Default: MILLISECONDS
         * Group: scheduler
         * 
         * @param timeUnit the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder timeUnit(String timeUnit) {
            doSetProperty("timeUnit", timeUnit);
            return this;
        }
        /**
         * Controls if fixed delay or fixed rate is used. See
         * ScheduledExecutorService in JDK for details.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: true
         * Group: scheduler
         * 
         * @param useFixedDelay the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder useFixedDelay(boolean useFixedDelay) {
            doSetProperty("useFixedDelay", useFixedDelay);
            return this;
        }
        /**
         * Controls if fixed delay or fixed rate is used. See
         * ScheduledExecutorService in JDK for details.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: true
         * Group: scheduler
         * 
         * @param useFixedDelay the value to set
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder useFixedDelay(String useFixedDelay) {
            doSetProperty("useFixedDelay", useFixedDelay);
            return this;
        }
    }

    /**
     * Advanced builder for endpoint for the PostgresSQL Replication Slot component.
     */
    public interface AdvancedPgReplicationSlotEndpointBuilder
            extends
                EndpointConsumerBuilder {
        default PgReplicationSlotEndpointBuilder basic() {
            return (PgReplicationSlotEndpointBuilder) this;
        }

        /**
         * Allows for bridging the consumer to the Camel routing Error Handler,
         * which mean any exceptions (if possible) occurred while the Camel
         * consumer is trying to pickup incoming messages, or the likes, will
         * now be processed as a message and handled by the routing Error
         * Handler. Important: This is only possible if the 3rd party component
         * allows Camel to be alerted if an exception was thrown. Some
         * components handle this internally only, and therefore
         * bridgeErrorHandler is not possible. In other situations we may
         * improve the Camel component to hook into the 3rd party component and
         * make this possible for future releases. By default the consumer will
         * use the org.apache.camel.spi.ExceptionHandler to deal with
         * exceptions, that will be logged at WARN or ERROR level and ignored.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: false
         * Group: consumer (advanced)
         * 
         * @param bridgeErrorHandler the value to set
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder bridgeErrorHandler(boolean bridgeErrorHandler) {
            doSetProperty("bridgeErrorHandler", bridgeErrorHandler);
            return this;
        }
        /**
         * Allows for bridging the consumer to the Camel routing Error Handler,
         * which mean any exceptions (if possible) occurred while the Camel
         * consumer is trying to pickup incoming messages, or the likes, will
         * now be processed as a message and handled by the routing Error
         * Handler. Important: This is only possible if the 3rd party component
         * allows Camel to be alerted if an exception was thrown. Some
         * components handle this internally only, and therefore
         * bridgeErrorHandler is not possible. In other situations we may
         * improve the Camel component to hook into the 3rd party component and
         * make this possible for future releases. By default the consumer will
         * use the org.apache.camel.spi.ExceptionHandler to deal with
         * exceptions, that will be logged at WARN or ERROR level and ignored.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: false
         * Group: consumer (advanced)
         * 
         * @param bridgeErrorHandler the value to set
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder bridgeErrorHandler(String bridgeErrorHandler) {
            doSetProperty("bridgeErrorHandler", bridgeErrorHandler);
            return this;
        }
        /**
         * To let the consumer use a custom ExceptionHandler. Notice if the
         * option bridgeErrorHandler is enabled then this option is not in use.
         * By default the consumer will deal with exceptions, that will be
         * logged at WARN or ERROR level and ignored.
         * 
         * The option is a: <code>org.apache.camel.spi.ExceptionHandler</code>
         * type.
         * 
         * Group: consumer (advanced)
         * 
         * @param exceptionHandler the value to set
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder exceptionHandler(org.apache.camel.spi.ExceptionHandler exceptionHandler) {
            doSetProperty("exceptionHandler", exceptionHandler);
            return this;
        }
        /**
         * To let the consumer use a custom ExceptionHandler. Notice if the
         * option bridgeErrorHandler is enabled then this option is not in use.
         * By default the consumer will deal with exceptions, that will be
         * logged at WARN or ERROR level and ignored.
         * 
         * The option will be converted to a
         * <code>org.apache.camel.spi.ExceptionHandler</code> type.
         * 
         * Group: consumer (advanced)
         * 
         * @param exceptionHandler the value to set
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder exceptionHandler(String exceptionHandler) {
            doSetProperty("exceptionHandler", exceptionHandler);
            return this;
        }
        /**
         * Sets the exchange pattern when the consumer creates an exchange.
         * 
         * The option is a: <code>org.apache.camel.ExchangePattern</code> type.
         * 
         * Group: consumer (advanced)
         * 
         * @param exchangePattern the value to set
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder exchangePattern(org.apache.camel.ExchangePattern exchangePattern) {
            doSetProperty("exchangePattern", exchangePattern);
            return this;
        }
        /**
         * Sets the exchange pattern when the consumer creates an exchange.
         * 
         * The option will be converted to a
         * <code>org.apache.camel.ExchangePattern</code> type.
         * 
         * Group: consumer (advanced)
         * 
         * @param exchangePattern the value to set
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder exchangePattern(String exchangePattern) {
            doSetProperty("exchangePattern", exchangePattern);
            return this;
        }
        /**
         * A pluggable org.apache.camel.PollingConsumerPollingStrategy allowing
         * you to provide your custom implementation to control error handling
         * usually occurred during the poll operation before an Exchange have
         * been created and being routed in Camel.
         * 
         * The option is a:
         * <code>org.apache.camel.spi.PollingConsumerPollStrategy</code> type.
         * 
         * Group: consumer (advanced)
         * 
         * @param pollStrategy the value to set
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder pollStrategy(org.apache.camel.spi.PollingConsumerPollStrategy pollStrategy) {
            doSetProperty("pollStrategy", pollStrategy);
            return this;
        }
        /**
         * A pluggable org.apache.camel.PollingConsumerPollingStrategy allowing
         * you to provide your custom implementation to control error handling
         * usually occurred during the poll operation before an Exchange have
         * been created and being routed in Camel.
         * 
         * The option will be converted to a
         * <code>org.apache.camel.spi.PollingConsumerPollStrategy</code> type.
         * 
         * Group: consumer (advanced)
         * 
         * @param pollStrategy the value to set
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder pollStrategy(String pollStrategy) {
            doSetProperty("pollStrategy", pollStrategy);
            return this;
        }
        /**
         * Auto create slot if it does not exist.
         * 
         * The option is a: <code>java.lang.Boolean</code> type.
         * 
         * Default: true
         * Group: advanced
         * 
         * @param autoCreateSlot the value to set
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder autoCreateSlot(Boolean autoCreateSlot) {
            doSetProperty("autoCreateSlot", autoCreateSlot);
            return this;
        }
        /**
         * Auto create slot if it does not exist.
         * 
         * The option will be converted to a <code>java.lang.Boolean</code>
         * type.
         * 
         * Default: true
         * Group: advanced
         * 
         * @param autoCreateSlot the value to set
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder autoCreateSlot(String autoCreateSlot) {
            doSetProperty("autoCreateSlot", autoCreateSlot);
            return this;
        }
        /**
         * Slot options to be passed to the output plugin. This is a multi-value
         * option with prefix: slotOptions.
         * 
         * The option is a: <code>java.util.Map&lt;java.lang.String,
         * java.lang.Object&gt;</code> type.
         * The option is multivalued, and you can use the slotOptions(String,
         * Object) method to add a value (call the method multiple times to set
         * more values).
         * 
         * Group: advanced
         * 
         * @param key the option key
         * @param value the option value
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder slotOptions(String key, Object value) {
            doSetMultiValueProperty("slotOptions", "slotOptions." + key, value);
            return this;
        }
        /**
         * Slot options to be passed to the output plugin. This is a multi-value
         * option with prefix: slotOptions.
         * 
         * The option is a: <code>java.util.Map&lt;java.lang.String,
         * java.lang.Object&gt;</code> type.
         * The option is multivalued, and you can use the slotOptions(String,
         * Object) method to add a value (call the method multiple times to set
         * more values).
         * 
         * Group: advanced
         * 
         * @param values the values
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder slotOptions(Map values) {
            doSetMultiValueProperties("slotOptions", "slotOptions.", values);
            return this;
        }
        /**
         * Specifies the number of seconds between status packets sent back to
         * Postgres server.
         * 
         * The option is a: <code>java.lang.Integer</code> type.
         * 
         * Default: 10
         * Group: advanced
         * 
         * @param statusInterval the value to set
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder statusInterval(Integer statusInterval) {
            doSetProperty("statusInterval", statusInterval);
            return this;
        }
        /**
         * Specifies the number of seconds between status packets sent back to
         * Postgres server.
         * 
         * The option will be converted to a <code>java.lang.Integer</code>
         * type.
         * 
         * Default: 10
         * Group: advanced
         * 
         * @param statusInterval the value to set
         * @return the dsl builder
         */
        default AdvancedPgReplicationSlotEndpointBuilder statusInterval(String statusInterval) {
            doSetProperty("statusInterval", statusInterval);
            return this;
        }
    }

    public interface PgReplicationSlotBuilders {
        /**
         * PostgresSQL Replication Slot (camel-pg-replication-slot)
         * Poll for PostgreSQL Write-Ahead Log (WAL) records using Streaming
         * Replication Slots.
         * 
         * Category: database
         * Since: 3.0
         * Maven coordinates: org.apache.camel:camel-pg-replication-slot
         * 
         * Syntax:
         * <code>pg-replication-slot:host:port/database/slot:outputPlugin</code>
         * 
         * Path parameter: slot (required)
         * Replication Slot name
         * 
         * Path parameter: host
         * Postgres host
         * Default value: localhost
         * 
         * Path parameter: port
         * Postgres port
         * Default value: 5432
         * 
         * Path parameter: database (required)
         * Postgres database name
         * 
         * Path parameter: outputPlugin (required)
         * Output plugin name
         * 
         * @param path host:port/database/slot:outputPlugin
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder pgReplicationSlot(String path) {
            return PgReplicationSlotEndpointBuilderFactory.endpointBuilder("pg-replication-slot", path);
        }
        /**
         * PostgresSQL Replication Slot (camel-pg-replication-slot)
         * Poll for PostgreSQL Write-Ahead Log (WAL) records using Streaming
         * Replication Slots.
         * 
         * Category: database
         * Since: 3.0
         * Maven coordinates: org.apache.camel:camel-pg-replication-slot
         * 
         * Syntax:
         * <code>pg-replication-slot:host:port/database/slot:outputPlugin</code>
         * 
         * Path parameter: slot (required)
         * Replication Slot name
         * 
         * Path parameter: host
         * Postgres host
         * Default value: localhost
         * 
         * Path parameter: port
         * Postgres port
         * Default value: 5432
         * 
         * Path parameter: database (required)
         * Postgres database name
         * 
         * Path parameter: outputPlugin (required)
         * Output plugin name
         * 
         * @param componentName to use a custom component name for the endpoint
         * instead of the default name
         * @param path host:port/database/slot:outputPlugin
         * @return the dsl builder
         */
        default PgReplicationSlotEndpointBuilder pgReplicationSlot(String componentName, String path) {
            return PgReplicationSlotEndpointBuilderFactory.endpointBuilder(componentName, path);
        }

    }
    static PgReplicationSlotEndpointBuilder endpointBuilder(String componentName, String path) {
        class PgReplicationSlotEndpointBuilderImpl extends AbstractEndpointBuilder implements PgReplicationSlotEndpointBuilder, AdvancedPgReplicationSlotEndpointBuilder {
            public PgReplicationSlotEndpointBuilderImpl(String path) {
                super(componentName, path);
            }
        }
        return new PgReplicationSlotEndpointBuilderImpl(path);
    }
}