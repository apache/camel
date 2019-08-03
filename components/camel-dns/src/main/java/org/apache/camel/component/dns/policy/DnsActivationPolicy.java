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
package org.apache.camel.component.dns.policy;

import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.LoggingExceptionHandler;
import org.apache.camel.support.RoutePolicySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DnsActivationPolicy extends RoutePolicySupport {
    private static final transient Logger LOG = LoggerFactory.getLogger(DnsActivationPolicy.class);

    private ExceptionHandler exceptionHandler;
    private DnsActivation dnsActivation;
    private long ttl;
    private boolean stopRoutesOnException;

    private Map<String, Route> routes = new ConcurrentHashMap<>();
    private Timer timer;

    public DnsActivationPolicy() {
        dnsActivation = new DnsActivation();
    }

    @Override
    public void onInit(Route route) {
        LOG.debug("onInit {}", route.getId());
        routes.put(route.getId(), route);
    }

    @Override
    public void onRemove(Route route) {
        LOG.debug("onRemove {}", route.getId());
        // noop
    }

    @Override
    public void onStart(Route route) {
        LOG.debug("onStart {}", route.getId());
        // noop
    }

    @Override
    public void onStop(Route route) {
        LOG.debug("onStop {}", route.getId());
        // noop
    }

    @Override
    public void onSuspend(Route route) {
        LOG.debug("onSuspend {}", route.getId());
        // noop
    }

    @Override
    public void onResume(Route route) {
        LOG.debug("onResume {}", route.getId());
        // noop
    }

    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
        LOG.debug("onExchange start " + route.getId() + "/" + exchange.getExchangeId());
        // noop
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        LOG.debug("onExchange end " + route.getId() + "/" + exchange.getExchangeId());
        // noop
    }

    @Override
    protected void doStart() throws Exception {
        LOG.debug("doStart");
        timer = new Timer();
        timer.schedule(new DnsActivationTask(), 0, ttl);
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("doStop");
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public ExceptionHandler getExceptionHandler() {
        if (exceptionHandler == null) {
            exceptionHandler = new LoggingExceptionHandler(null, getClass());
        }
        return exceptionHandler;
    }

    @Override
    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public void setHostname(String hostname) {
        dnsActivation.setHostname(hostname);
    }

    public String getHostname() {
        return dnsActivation.getHostname();
    }

    public void setResolvesTo(List<String> resolvesTo) {
        dnsActivation.setResolvesTo(resolvesTo);
    }

    public void setResolvesTo(String resolvesTo) {
        dnsActivation.setResolvesTo(resolvesTo);
    }

    public List<String> getResolvesTo() {
        return dnsActivation.getResolvesTo();
    }

    public void setTtl(long ttl) throws Exception {
        this.ttl = ttl;
    }

    public void setTtl(String ttl) throws Exception {
        this.ttl = Long.parseLong(ttl);
    }

    public long getTtl() throws Exception {
        return ttl;
    }

    public void setStopRoutesOnException(String stopRoutesOnException) throws Exception {
        this.stopRoutesOnException = Boolean.parseBoolean(stopRoutesOnException);
    }

    private void startRouteImpl(Route route) throws Exception {
        ServiceStatus routeStatus = controller(route).getRouteStatus(route.getId());

        if (routeStatus == ServiceStatus.Stopped) {
            LOG.info("Starting {}", route.getId());
            startRoute(route);
        } else if (routeStatus == ServiceStatus.Suspended) {
            LOG.info("Resuming {}", route.getId());
            startConsumer(route.getConsumer());
        } else {
            LOG.debug("Nothing to do " + route.getId() + " is " + routeStatus);
        }
    }

    private void startRoutes() {
        for (String routeId : routes.keySet()) {
            try {
                Route route = routes.get(routeId);
                startRouteImpl(route);
            } catch (Exception e) {
                LOG.warn(routeId, e);
            }
        }
    }

    private void stopRouteImpl(Route route) throws Exception {
        ServiceStatus routeStatus = controller(route).getRouteStatus(route.getId());

        if (routeStatus == ServiceStatus.Started) {
            LOG.info("Stopping {}", route.getId());
            stopRoute(route);
        } else {
            LOG.debug("Nothing to do " + route.getId() + " is " + routeStatus);
        }
    }

    private void stopRoutes() {
        for (String routeId : routes.keySet()) {
            try {
                Route route = routes.get(routeId);
                stopRouteImpl(route);
            } catch (Exception e) {
                LOG.warn(routeId, e);
            }
        }
    }

    protected boolean isActive() throws Exception {
        return dnsActivation.isActive();
    }

    class DnsActivationTask extends TimerTask {
        @Override
        public void run() {
            try {
                if (isActive()) {
                    startRoutes();
                } else {
                    stopRoutes();
                }
            } catch (Exception e) {
                LOG.warn("DnsActivation TimerTask failed", e);
                if (stopRoutesOnException) {
                    stopRoutes();
                }
            }
        }
    }
}


