/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.graalvm;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Service;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.ShutdownStrategy;

public class NoShutdownStrategy implements ShutdownStrategy {

    @Override
    public void shutdownForced(CamelContext context, List<RouteStartupOrder> routes) throws Exception {

    }

    @Override
    public void shutdown(CamelContext context, List<RouteStartupOrder> routes) throws Exception {

    }

    @Override
    public void suspend(CamelContext context, List<RouteStartupOrder> routes) throws Exception {

    }

    @Override
    public void shutdown(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit) throws Exception {

    }

    @Override
    public boolean shutdown(CamelContext context, RouteStartupOrder route, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception {
        return false;
    }

    @Override
    public void suspend(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit) throws Exception {

    }

    @Override
    public void setTimeout(long timeout) {

    }

    @Override
    public long getTimeout() {
        return 0;
    }

    @Override
    public void setTimeUnit(TimeUnit timeUnit) {

    }

    @Override
    public TimeUnit getTimeUnit() {
        return null;
    }

    @Override
    public void setSuppressLoggingOnTimeout(boolean suppressLoggingOnTimeout) {

    }

    @Override
    public boolean isSuppressLoggingOnTimeout() {
        return false;
    }

    @Override
    public void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout) {

    }

    @Override
    public boolean isShutdownNowOnTimeout() {
        return false;
    }

    @Override
    public void setShutdownRoutesInReverseOrder(boolean shutdownRoutesInReverseOrder) {

    }

    @Override
    public boolean isShutdownRoutesInReverseOrder() {
        return false;
    }

    @Override
    public void setLogInflightExchangesOnTimeout(boolean logInflightExchangesOnTimeout) {

    }

    @Override
    public boolean isLogInflightExchangesOnTimeout() {
        return false;
    }

    @Override
    public boolean forceShutdown(Service service) {
        return false;
    }

    @Override
    public boolean hasTimeoutOccurred() {
        return false;
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {

    }
}
