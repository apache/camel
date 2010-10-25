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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.ShutdownableService;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * A useful base class which ensures that a service is only initialized once and
 * provides some helper methods for enquiring of its status.
 * <p/>
 * Implementations can extend this base class and implement {@link org.apache.camel.SuspendableService}
 * in case they support suspend/resume.
 *
 * @version $Revision$
 */
public abstract class ServiceSupport implements Service, ShutdownableService {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean suspending = new AtomicBoolean(false);
    private final AtomicBoolean suspended = new AtomicBoolean(false);
    private final AtomicBoolean shuttingdown = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private Set<Object> childServices;
    private String version;

    public void start() throws Exception {
        start(true);
    }

    public void start(boolean startChildren) throws Exception {
        if (!started.get()) {
            if (starting.compareAndSet(false, true)) {
                boolean childrenStarted = false;
                Exception ex = null;
                try {
                    if (childServices != null && startChildren) {
                        ServiceHelper.startServices(childServices);
                    }
                    childrenStarted = true;
                    doStart();
                } catch (Exception e) {
                    ex = e;
                } finally {
                    if (ex != null) {
                        try {
                            stop(childrenStarted);
                        } catch (Exception e) {
                            // Ignore exceptions as we want to show the original exception
                        }
                        throw ex;
                    } else {
                        started.set(true);
                        starting.set(false);
                        stopping.set(false);
                        stopped.set(false);
                        suspending.set(false);
                        suspended.set(false);
                        shutdown.set(false);
                        shuttingdown.set(false);
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
                    suspending.set(false);
                    if (childrenStarted) {
                        doStop();
                    }
                } finally {
                    started.set(false);
                    suspended.set(false);
                    if (childServices != null) {
                        ServiceHelper.stopServices(childServices);
                    }
                }
            } finally {
                stopped.set(true);
                stopping.set(false);
                starting.set(false);
                started.set(false);
                suspending.set(false);
                suspended.set(false);
                shutdown.set(false);
                shuttingdown.set(false);
            }
        }
    }

    public void stop() throws Exception {
        if (!stopped.get()) {
            stop(true);
        }
    }

    public void suspend() throws Exception {
        if (!suspended.get()) {
            if (suspending.compareAndSet(false, true)) {
                try {
                    starting.set(false);
                    stopping.set(false);
                    doSuspend();
                } finally {
                    stopped.set(false);
                    stopping.set(false);
                    starting.set(false);
                    started.set(false);
                    suspending.set(false);
                    suspended.set(true);
                    shutdown.set(false);
                    shuttingdown.set(false);
                }
            }
        }
    }

    public void resume() throws Exception {
        if (suspended.get()) {
            if (starting.compareAndSet(false, true)) {
                try {
                    doResume();
                } finally {
                    started.set(true);
                    starting.set(false);
                    stopping.set(false);
                    stopped.set(false);
                    suspending.set(false);
                    suspended.set(false);
                    shutdown.set(false);
                    shuttingdown.set(false);
                }
            }
        }
    }

    public void shutdown() throws Exception {
        // ensure we are stopped first
        stop();

        if (shuttingdown.compareAndSet(false, true)) {
            try {
                try {
                    doShutdown();
                } finally {
                    if (childServices != null) {
                        ServiceHelper.stopAndShutdownService(childServices);
                    }
                }
            } finally {
                // shutdown is also stopped so only set shutdown flags
                shutdown.set(true);
                shuttingdown.set(false);
            }
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
        if (isSuspending()) {
            return ServiceStatus.Suspending;
        }
        if (isSuspended()) {
            return ServiceStatus.Suspended;
        }

        // use stopped as fallback
        return ServiceStatus.Stopped;
    }
    
    /**
     * @return true if this service has been started
     */
    public boolean isStarted() {
        return started.get();
    }

    /**
     * @return true if this service is being started
     */
    public boolean isStarting() {
        return starting.get();
    }

    /**
     * @return true if this service is in the process of stopping
     */
    public boolean isStopping() {
        return stopping.get();
    }

    /**
     * @return true if this service is stopped
     */
    public boolean isStopped() {
        return stopped.get();
    }

    /**
     * @return true if this service is in the process of suspending
     */
    public boolean isSuspending() {
        return suspending.get();
    }

    /**
     * @return true if this service is suspended
     */
    public boolean isSuspended() {
        return suspended.get();
    }

    /**
     * Helper methods so the service knows if it should keep running.
     * Returns <tt>false</tt> if the service is being stopped or is stopped.
     *
     * @return <tt>true</tt> if the service should continue to run.
     */
    public boolean isRunAllowed() {
        return !(stopping.get() || stopped.get());
    }

    protected abstract void doStart() throws Exception;

    protected abstract void doStop() throws Exception;

    /**
     * Implementations override this method to support customized suspend/resume.
     */
    protected void doSuspend() throws Exception {
    }

    /**
     * Implementations override this method to support customized suspend/resume.
     */
    protected void doResume() throws Exception {
    }

    /**
     * Implementations override this method to perform customized shutdown
     */
    protected void doShutdown() throws Exception {
        // noop
    }

    @SuppressWarnings("unchecked")
    protected void addChildService(Object childService) {
        synchronized (this) {
            if (childServices == null) {
                childServices = new LinkedHashSet();
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
