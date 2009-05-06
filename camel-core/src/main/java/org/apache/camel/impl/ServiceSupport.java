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
package org.apache.camel.impl;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * A useful base class which ensures that a service is only initialized once and
 * provides some helper methods for enquiring of its status
 *
 * @version $Revision$
 */
public abstract class ServiceSupport implements Service {
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private Collection childServices;
    private String version;

    public void start() throws Exception {
        if (!started.get()) {
            if (starting.compareAndSet(false, true)) {
                boolean childrenStarted = false;
                Exception ex = null;
                try {
                    if (childServices != null) {
                        ServiceHelper.startServices(childServices);
                    }
                    childrenStarted = true;
                    doStart();
                } catch (Exception e) {
                    ex = e;
                } finally {
                    if (ex != null) {
                        stop(childrenStarted);
                        throw ex;
                    } else {
                        started.set(true);
                        starting.set(false);
                    }
                }
            }
        }
    }
    
    private void stop(boolean childrenStarted) throws Exception {
        if (stopping.compareAndSet(false, true)) {
            try {
                try {
                    starting.set(false);
                    if (childrenStarted) {
                        doStop();
                    }
                } finally {
                    started.set(false);
                    if (childServices != null) {
                        ServiceHelper.stopServices(childServices);
                    }
                }
            } finally {
                stopped.set(true);
                stopping.set(false);
            }
        }
    }

    public void stop() throws Exception {
        if (started.get()) {
            stop(true);
        }
    }

    /**
     * Returns the current status
     */
    public ServiceStatus getStatus() {
        // lets check these in oldest first as these flags can be changing in a concurrent world
        if (isStarting()) {
            return ServiceStatus.Starting;
        }
        if (isStarted()) {
            return ServiceStatus.Started;
        }
        if (isStopping()) {
            return ServiceStatus.Stopping;
        }
        if (isStopped()) {
            return ServiceStatus.Stopped;
        }
        return ServiceStatus.Created;
    }
    
    /**
     * @return true if this service has been started
     */
    public boolean isStarted() {
        return started.get();
    }

    /**
     * @return true if this service is
     */
    public boolean isStarting() {
        return starting.get();
    }

    /**
     * @return true if this service is in the process of closing
     */
    public boolean isStopping() {
        return stopping.get();
    }

    /**
     * @return true if this service is closed
     */
    public boolean isStopped() {
        return stopped.get();
    }

    /**
     * Helper methods so the service knows if it should keep running.
     * Returns false if the service is being stopped or is stopped.
     *
     * @return true if the service should continue to run.
     */
    protected boolean isRunAllowed() {
        return !(stopping.get() || stopped.get());
    }

    protected abstract void doStart() throws Exception;

    protected abstract void doStop() throws Exception;

    @SuppressWarnings("unchecked")
    protected void addChildService(Object childService) {
        synchronized (this) {
            if (childServices == null) {
                childServices = new CopyOnWriteArrayList();
            }
        }
        childServices.add(childService);
    }

    protected boolean removeChildService(Object childService) {
        return childServices != null && childServices.remove(childService);
    }

    /**
     * Returns the version of this service
     */
    public synchronized String getVersion() {
        if (ObjectHelper.isNotEmpty(version)) {
            return version;
        }
        
        Package aPackage = getClass().getPackage();
        if (aPackage != null) {
            version = aPackage.getImplementationVersion();
            if (version == null) {
                version = aPackage.getSpecificationVersion();
            }
        }
        return version != null ? version : "";
    }
}
