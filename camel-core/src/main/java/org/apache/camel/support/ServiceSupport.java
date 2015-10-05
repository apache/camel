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

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A useful base class which ensures that a service is only initialized once and
 * provides some helper methods for enquiring of its status.
 * <p/>
 * Implementations can extend this base class and implement {@link org.apache.camel.SuspendableService}
 * in case they support suspend/resume.
 *
 * @version 
 */
public abstract class ServiceSupport implements StatefulService {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceSupport.class);

    protected final AtomicBoolean started = new AtomicBoolean(false);
    protected final AtomicBoolean starting = new AtomicBoolean(false);
    protected final AtomicBoolean stopping = new AtomicBoolean(false);
    protected final AtomicBoolean stopped = new AtomicBoolean(false);
    protected final AtomicBoolean suspending = new AtomicBoolean(false);
    protected final AtomicBoolean suspended = new AtomicBoolean(false);
    protected final AtomicBoolean shuttingdown = new AtomicBoolean(false);
    protected final AtomicBoolean shutdown = new AtomicBoolean(false);

    private String version;

    public void start() throws Exception {
        if (isStarting() || isStarted()) {
            // only start service if not already started
            LOG.trace("Service already started");
            return;
        }
        if (starting.compareAndSet(false, true)) {
            LOG.trace("Starting service");
            try {
                doStart();
                started.set(true);
                starting.set(false);
                stopping.set(false);
                stopped.set(false);
                suspending.set(false);
                suspended.set(false);
                shutdown.set(false);
                shuttingdown.set(false);
            } catch (Exception e) {
                try {
                    stop();
                } catch (Exception e2) {
                    // Ignore exceptions as we want to show the original exception
                } finally {
                    // ensure flags get reset to stopped as we failed during starting
                    stopping.set(false);
                    stopped.set(true);
                    starting.set(false);
                    started.set(false);
                    suspending.set(false);
                    suspended.set(false);
                    shutdown.set(false);
                    shuttingdown.set(false);
                }
                throw e;
            } 
        }
    }
    
    public void stop() throws Exception {
        if (isStopped()) {
            LOG.trace("Service already stopped");
            return;
        }
        if (isStopping()) {
            LOG.trace("Service already stopping");
            return;
        }
        stopping.set(true);
        try {
            doStop();
        } finally {
            stopping.set(false);
            stopped.set(true);
            starting.set(false);
            started.set(false);
            suspending.set(false);
            suspended.set(false);
            shutdown.set(false);
            shuttingdown.set(false);            
        }
    }

    @Override
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

    @Override
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

    @Override
    public void shutdown() throws Exception {
        if (shutdown.get()) {
            LOG.trace("Service already shut down");
            return;
        }
        // ensure we are stopped first
        stop();

        if (shuttingdown.compareAndSet(false, true)) {
            try {
                doShutdown();
            } finally {
                // shutdown is also stopped so only set shutdown flags
                shutdown.set(true);
                shuttingdown.set(false);
            }
        }
    }

    @Override
    public ServiceStatus getStatus() {
        // we should check the ---ing states first, as this indicate the state is in the middle of doing that
        if (isStarting()) {
            return ServiceStatus.Starting;
        }
        if (isStopping()) {
            return ServiceStatus.Stopping;
        }
        if (isSuspending()) {
            return ServiceStatus.Suspending;
        }

        // then check for the regular states
        if (isStarted()) {
            return ServiceStatus.Started;
        }
        if (isStopped()) {
            return ServiceStatus.Stopped;
        }
        if (isSuspended()) {
            return ServiceStatus.Suspended;
        }

        // use stopped as fallback
        return ServiceStatus.Stopped;
    }
    
    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isStarting() {
        return starting.get();
    }

    @Override
    public boolean isStopping() {
        return stopping.get();
    }

    @Override
    public boolean isStopped() {
        return stopped.get();
    }

    @Override
    public boolean isSuspending() {
        return suspending.get();
    }

    @Override
    public boolean isSuspended() {
        return suspended.get();
    }

    @Override
    public boolean isRunAllowed() {
        // if we have not yet initialized, then all options is false
        boolean unused1 = !started.get() && !starting.get() && !stopping.get() && !stopped.get();
        boolean unused2 = !suspending.get() && !suspended.get() && !shutdown.get() && !shuttingdown.get();
        if (unused1 && unused2) {
            return false;
        }
        return !isStoppingOrStopped();
    }

    /**
     * Is the service in progress of being stopped or already stopped
     */
    public boolean isStoppingOrStopped() {
        return stopping.get() || stopped.get();
    }

    /**
     * Is the service in progress of being suspended or already suspended
     */
    public boolean isSuspendingOrSuspended() {
        return suspending.get() || suspended.get();
    }

    /**
     * Implementations override this method to support customized start/stop.
     * <p/>
     * <b>Important: </b> See {@link #doStop()} for more details.
     * 
     * @see #doStop()
     */
    protected abstract void doStart() throws Exception;

    /**
     * Implementations override this method to support customized start/stop.
     * <p/>
     * <b>Important:</b> Camel will invoke this {@link #doStop()} method when
     * the service is being stopped. This method will <b>also</b> be invoked
     * if the service is still in <i>uninitialized</i> state (eg has not
     * been started). The method is <b>always</b> called to allow the service
     * to do custom logic when the service is being stopped, such as when
     * {@link org.apache.camel.CamelContext} is shutting down.
     * 
     * @see #doStart() 
     */
    protected abstract void doStop() throws Exception;

    /**
     * Implementations override this method to support customized suspend/resume.
     */
    protected void doSuspend() throws Exception {
        // noop
    }

    /**
     * Implementations override this method to support customized suspend/resume.
     */
    protected void doResume() throws Exception {
        // noop
    }

    /**
     * Implementations override this method to perform customized shutdown.
     */
    protected void doShutdown() throws Exception {
        // noop
    }

    @Override
    public synchronized String getVersion() {
        if (version != null) {
            return version;
        }
        InputStream is = null;
        // try to load from maven properties first
        try {
            Properties p = new Properties();
            is = getClass().getResourceAsStream("/META-INF/maven/org.apache.camel/camel-core/pom.properties");
            if (is != null) {
                p.load(is);
                version = p.getProperty("version", "");
            }
        } catch (Exception e) {
            // ignore
        } finally {
            if (is != null) {
                IOHelper.close(is);
            }
        }

        // fallback to using Java API
        if (version == null) {
            Package aPackage = getClass().getPackage();
            if (aPackage != null) {
                version = aPackage.getImplementationVersion();
                if (version == null) {
                    version = aPackage.getSpecificationVersion();
                }
            }
        }

        if (version == null) {
            // we could not compute the version so use a blank
            version = "";
        }

        return version;
    }
}
