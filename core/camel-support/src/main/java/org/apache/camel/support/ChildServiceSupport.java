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
package org.apache.camel.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Base class to control lifecycle for a set of child {@link org.apache.camel.Service}s.
 */
public abstract class ChildServiceSupport extends ServiceSupport {

    protected volatile List<Service> childServices;

    @Override
    public void start() {
        synchronized (lock) {
            if (status == STARTED) {
                log.trace("Service: {} already started", this);
                return;
            }
            if (status == STARTING) {
                log.trace("Service: {} already starting", this);
                return;
            }
            try {
                initService(childServices);
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error while initializing service: " + this, e);
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
            try {
                status = STARTING;
                log.trace("Starting service: {}", this);
                ServiceHelper.startService(childServices);
                doStart();
                status = STARTED;
                log.trace("Service: {} started", this);
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error while starting service: " + this, e);
                ServiceHelper.stopService(childServices);
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (status == STOPPED || status == SHUTTINGDOWN || status == SHUTDOWN) {
                log.trace("Service: {} already stopped", this);
                return;
            }
            if (status == STOPPING) {
                log.trace("Service: {} already stopping", this);
                return;
            }
            status = STOPPING;
            log.trace("Stopping service: {}", this);
            try {
                doStop();
                ServiceHelper.stopService(childServices);
                status = STOPPED;
                log.trace("Service: {} stopped service", this);
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error while stopping service: " + this, e);
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            if (status == SHUTDOWN) {
                log.trace("Service: {} already shut down", this);
                return;
            }
            if (status == SHUTTINGDOWN) {
                log.trace("Service: {} already shutting down", this);
                return;
            }
            stop();
            status = SHUTDOWN;
            log.trace("Shutting down service: {}", this);
            try {
                doShutdown();
                ServiceHelper.stopAndShutdownServices(childServices);
                log.trace("Service: {} shut down", this);
                status = SHUTDOWN;
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error shutting down service: " + this, e);
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    protected void addChildService(Object childService) {
        if (childService instanceof Service) {
            if (childServices == null) {
                synchronized (lock) {
                    if (childServices == null) {
                        childServices = new CopyOnWriteArrayList<>();
                    }
                }
            }
            childServices.add((Service) childService);
        }
    }

    protected boolean removeChildService(Object childService) {
        return childServices != null && childServices.remove(childService);
    }

    private void initService(List<Service> services) {
        if (services != null) {
            services.forEach(Service::init);
        }
    }

}
