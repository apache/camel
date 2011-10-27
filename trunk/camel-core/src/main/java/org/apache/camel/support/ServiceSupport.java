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
    private static final transient Logger LOG = LoggerFactory.getLogger(ServiceSupport.class);

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

    /* (non-Javadoc)
     * @see org.apache.camel.support.StatefulService#suspend()
     */
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

    /* (non-Javadoc)
     * @see org.apache.camel.support.StatefulService#resume()
     */
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

    /* (non-Javadoc)
     * @see org.apache.camel.support.StatefulService#shutdown()
     */
    @Override
    public void shutdown() throws Exception {
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

    /* (non-Javadoc)
     * @see org.apache.camel.support.StatefulService#getStatus()
     */
    @Override
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
    
    /* (non-Javadoc)
     * @see org.apache.camel.support.StatefulService#isStarted()
     */
    @Override
    public boolean isStarted() {
        return started.get();
    }

    /* (non-Javadoc)
     * @see org.apache.camel.support.StatefulService#isStarting()
     */
    @Override
    public boolean isStarting() {
        return starting.get();
    }

    /* (non-Javadoc)
     * @see org.apache.camel.support.StatefulService#isStopping()
     */
    @Override
    public boolean isStopping() {
        return stopping.get();
    }

    /* (non-Javadoc)
     * @see org.apache.camel.support.StatefulService#isStopped()
     */
    @Override
    public boolean isStopped() {
        return stopped.get();
    }

    /* (non-Javadoc)
     * @see org.apache.camel.support.StatefulService#isSuspending()
     */
    @Override
    public boolean isSuspending() {
        return suspending.get();
    }

    /* (non-Javadoc)
     * @see org.apache.camel.support.StatefulService#isSuspended()
     */
    @Override
    public boolean isSuspended() {
        return suspended.get();
    }

    /* (non-Javadoc)
     * @see org.apache.camel.support.StatefulService#isRunAllowed()
     */
    @Override
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

    /* (non-Javadoc)
     * @see org.apache.camel.support.StatefulService#getVersion()
     */
    @Override
    public synchronized String getVersion() {
        if (version != null) {
            return version;
        }

        // try to load from maven properties first
        try {
            Properties p = new Properties();
            InputStream is = getClass().getResourceAsStream("/META-INF/maven/org.apache.camel/camel-core/pom.properties");
            if (is != null) {
                p.load(is);
                version = p.getProperty("version", "");
            }
        } catch (Exception e) {
            // ignore
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
