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
package org.apache.camel.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Service;

/**
 * Base class to control lifecycle for a set of child {@link org.apache.camel.Service}s.
 */
public abstract class ChildServiceSupport extends ServiceSupport {

    protected volatile List<Service> childServices;

    public void start() throws Exception {
        synchronized (lock) {
            if (status == STARTED) {
                log.trace("Service already started");
                return;
            }
            if (status == STARTING) {
                log.trace("Service already starting");
                return;
            }
            status = STARTING;
            log.trace("Starting service");
            try {
                ServiceHelper.startService(childServices);
                doStart();
                status = STARTED;
                log.trace("Service started");
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error while starting service", e);
                ServiceHelper.stopService(childServices);
                throw e;
            }
        }
    }

    public void stop() throws Exception {
        synchronized (lock) {
            if (status == STOPPED || status == SHUTTINGDOWN || status == SHUTDOWN) {
                log.trace("Service already stopped");
                return;
            }
            if (status == STOPPING) {
                log.trace("Service already stopping");
                return;
            }
            status = STOPPING;
            log.trace("Stopping service");
            try {
                doStop();
                ServiceHelper.stopService(childServices);
                status = STOPPED;
                log.trace("Service stopped service");
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error while stopping service", e);
                throw e;
            }
        }
    }

    @Override
    public void shutdown() throws Exception {
        synchronized (lock) {
            if (status == SHUTDOWN) {
                log.trace("Service already shut down");
                return;
            }
            if (status == SHUTTINGDOWN) {
                log.trace("Service already shutting down");
                return;
            }
            stop();
            status = SHUTDOWN;
            log.trace("Shutting down service");
            try {
                doShutdown();
                ServiceHelper.stopAndShutdownServices(childServices);
                log.trace("Service shut down");
                status = SHUTDOWN;
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error shutting down service", e);
                throw e;
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

}
