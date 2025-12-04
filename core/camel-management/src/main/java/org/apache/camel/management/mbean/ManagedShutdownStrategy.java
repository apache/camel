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

package org.apache.camel.management.mbean;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedShutdownStrategyMBean;
import org.apache.camel.spi.ShutdownStrategy;

@ManagedResource(description = "Managed ShutdownStrategy")
public class ManagedShutdownStrategy extends ManagedService implements ManagedShutdownStrategyMBean {

    private final ShutdownStrategy strategy;

    public ManagedShutdownStrategy(CamelContext context, ShutdownStrategy controller) {
        super(context, controller);
        this.strategy = controller;
    }

    public ShutdownStrategy getShutdownStrategy() {
        return strategy;
    }

    @Override
    public void setTimeout(long timeout) {
        strategy.setTimeout(timeout);
    }

    @Override
    public long getTimeout() {
        return strategy.getTimeout();
    }

    @Override
    public void setTimeUnit(TimeUnit timeUnit) {
        strategy.setTimeUnit(timeUnit);
    }

    @Override
    public TimeUnit getTimeUnit() {
        return strategy.getTimeUnit();
    }

    @Override
    public void setSuppressLoggingOnTimeout(boolean suppressLoggingOnTimeout) {
        strategy.setSuppressLoggingOnTimeout(suppressLoggingOnTimeout);
    }

    @Override
    public boolean isSuppressLoggingOnTimeout() {
        return strategy.isSuppressLoggingOnTimeout();
    }

    @Override
    public void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout) {
        strategy.setShutdownNowOnTimeout(shutdownNowOnTimeout);
    }

    @Override
    public boolean isShutdownNowOnTimeout() {
        return strategy.isShutdownNowOnTimeout();
    }

    @Override
    public void setShutdownRoutesInReverseOrder(boolean shutdownRoutesInReverseOrder) {
        strategy.setShutdownRoutesInReverseOrder(shutdownRoutesInReverseOrder);
    }

    @Override
    public boolean isShutdownRoutesInReverseOrder() {
        return strategy.isShutdownRoutesInReverseOrder();
    }

    @Override
    public void setLogInflightExchangesOnTimeout(boolean logInflightExchangesOnTimeout) {
        strategy.setLogInflightExchangesOnTimeout(logInflightExchangesOnTimeout);
    }

    @Override
    public boolean isLogInflightExchangesOnTimeout() {
        return strategy.isLogInflightExchangesOnTimeout();
    }

    @Override
    public boolean isForceShutdown() {
        return strategy.isForceShutdown();
    }

    @Override
    public boolean isTimeoutOccurred() {
        return strategy.isTimeoutOccurred();
    }

    @Override
    public String getLoggingLevel() {
        return strategy.getLoggingLevel().toString();
    }

    @Override
    public void setLoggingLevel(String loggingLevel) {
        strategy.setLoggingLevel(LoggingLevel.valueOf(loggingLevel));
    }
}
