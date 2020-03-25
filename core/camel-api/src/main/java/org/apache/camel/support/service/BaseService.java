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
package org.apache.camel.support.service;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A useful base class which ensures that a service is only initialized once and
 * provides some helper methods for enquiring of its status.
 * <p/>
 * Implementations can extend this base class and implement {@link org.apache.camel.SuspendableService}
 * in case they support suspend/resume.
 * <p/>
 * <b>Important: </b> You should override the lifecycle methods that start with <tt>do</tt>, eg {@link #doStart()}},
 * {@link #doStop()}, etc. where you implement your logic. The methods {@link #start()}, {@link #stop()} should
 * <b>NOT</b> be overridden as they are used internally to keep track of the state of this service and properly
 * invoke the operation in a safe manner.
 */
public abstract class BaseService {

    protected static final byte NEW = 0;
    protected static final byte BUILDED = 1;
    protected static final byte INITIALIZING = 2;
    protected static final byte INITIALIZED = 3;
    protected static final byte STARTING = 4;
    protected static final byte STARTED = 5;
    protected static final byte SUSPENDING = 6;
    protected static final byte SUSPENDED = 7;
    protected static final byte STOPPING = 8;
    protected static final byte STOPPED = 9;
    protected static final byte SHUTTINGDOWN = 10;
    protected static final byte SHUTDOWN = 11;
    protected static final byte FAILED = 12;

    private static final Logger LOG = LoggerFactory.getLogger(BaseService.class);

    protected final Object lock = new Object();
    protected volatile byte status = NEW;

    public void build() {
        if (status == NEW) {
            synchronized (lock) {
                if (status == NEW) {
                    LOG.trace("Building service: {}", this);
                    try (AutoCloseable ignored = doLifecycleChange()) {
                        doBuild();
                    } catch (Exception e) {
                        doFail(e);
                    }
                    status = BUILDED;
                    LOG.trace("Built service: {}", this);
                }
            }
        }
    }

    public void init() {
        // allow to initialize again if stopped or failed
        if (status <= BUILDED || status >= STOPPED) {
            synchronized (lock) {
                if (status <= BUILDED || status >= STOPPED) {
                    build();
                    LOG.trace("Initializing service: {}", this);
                    try (AutoCloseable ignored = doLifecycleChange()) {
                        status = INITIALIZING;
                        doInit();
                        status = INITIALIZED;
                        LOG.trace("Initialized service: {}", this);
                    } catch (Exception e) {
                        LOG.trace("Error while initializing service: " + this, e);
                        fail(e);
                    }
                }
            }
        }
    }

    /**
     * <b>Important: </b> You should override the lifecycle methods that start with <tt>do</tt>, eg {@link #doStart()},
     * {@link #doStop()}, etc. where you implement your logic. The methods {@link #start()}, {@link #stop()} should
     * <b>NOT</b> be overridden as they are used internally to keep track of the state of this service and properly
     * invoke the operation in a safe manner.
     */
    public void start() {
        synchronized (lock) {
            if (status == STARTED) {
                LOG.trace("Service: {} already started", this);
                return;
            }
            if (status == STARTING) {
                LOG.trace("Service: {} already starting", this);
                return;
            }
            init();
            try (AutoCloseable ignored = doLifecycleChange()) {
                status = STARTING;
                LOG.trace("Starting service: {}", this);
                doStart();
                status = STARTED;
                LOG.trace("Started service: {}", this);
            } catch (Exception e) {
                // need to stop as some resources may have been started during startup
                try {
                    stop();
                } catch (Exception e2) {
                    // ignore
                    LOG.trace("Error while stopping service after it failed to start: " + this + ". This exception is ignored", e);
                }
                LOG.trace("Error while starting service: " + this, e);
                fail(e);
            }
        }
    }

    /**
     * <b>Important: </b> You should override the lifecycle methods that start with <tt>do</tt>, eg {@link #doStart()},
     * {@link #doStop()}, etc. where you implement your logic. The methods {@link #start()}, {@link #stop()} should
     * <b>NOT</b> be overridden as they are used internally to keep track of the state of this service and properly
     * invoke the operation in a safe manner.
     */
    public void stop() {
        synchronized (lock) {
            if (status == FAILED) {
                LOG.trace("Service: {} failed and regarded as already stopped", this);
                return;
            }
            if (status == STOPPED || status == SHUTTINGDOWN || status == SHUTDOWN) {
                LOG.trace("Service: {} already stopped", this);
                return;
            }
            if (status == STOPPING) {
                LOG.trace("Service: {} already stopping", this);
                return;
            }
            status = STOPPING;
            LOG.trace("Stopping service: {}", this);
            try (AutoCloseable ignored = doLifecycleChange()) {
                doStop();
                status = STOPPED;
                LOG.trace("Stopped: {} service", this);
            } catch (Exception e) {
                LOG.trace("Error while stopping service: " + this, e);
                fail(e);
            }
        }
    }

    /**
     * <b>Important: </b> You should override the lifecycle methods that start with <tt>do</tt>, eg {@link #doStart()},
     * {@link #doStop()}, etc. where you implement your logic. The methods {@link #start()}, {@link #stop()} should
     * <b>NOT</b> be overridden as they are used internally to keep track of the state of this service and properly
     * invoke the operation in a safe manner.
     */
    public void suspend() {
        synchronized (lock) {
            if (status == SUSPENDED) {
                LOG.trace("Service: {} already suspended", this);
                return;
            }
            if (status == SUSPENDING) {
                LOG.trace("Service: {} already suspending", this);
                return;
            }
            status = SUSPENDING;
            LOG.trace("Suspending service: {}", this);
            try (AutoCloseable ignored = doLifecycleChange()) {
                doSuspend();
                status = SUSPENDED;
                LOG.trace("Suspended service: {}", this);
            } catch (Exception e) {
                LOG.trace("Error while suspending service: " + this, e);
                fail(e);
            }
        }
    }

    /**
     * <b>Important: </b> You should override the lifecycle methods that start with <tt>do</tt>, eg {@link #doStart()},
     * {@link #doStop()}, etc. where you implement your logic. The methods {@link #start()}, {@link #stop()} should
     * <b>NOT</b> be overridden as they are used internally to keep track of the state of this service and properly
     * invoke the operation in a safe manner.
     */
    public void resume() {
        synchronized (lock) {
            if (status != SUSPENDED) {
                LOG.trace("Service is not suspended: {}", this);
                return;
            }
            status = STARTING;
            LOG.trace("Resuming service: {}", this);
            try (AutoCloseable ignored = doLifecycleChange()) {
                doResume();
                status = STARTED;
                LOG.trace("Resumed service: {}", this);
            } catch (Exception e) {
                LOG.trace("Error while resuming service: " + this, e);
                fail(e);
            }
        }
    }

    /**
     * <b>Important: </b> You should override the lifecycle methods that start with <tt>do</tt>, eg {@link #doStart()},
     * {@link #doStop()}, etc. where you implement your logic. The methods {@link #start()}, {@link #stop()} should
     * <b>NOT</b> be overridden as they are used internally to keep track of the state of this service and properly
     * invoke the operation in a safe manner.
     */
    public void shutdown() {
        synchronized (lock) {
            if (status == SHUTDOWN) {
                LOG.trace("Service: {} already shutdown", this);
                return;
            }
            if (status == SHUTTINGDOWN) {
                LOG.trace("Service: {} already shutting down", this);
                return;
            }
            stop();
            status = SHUTDOWN;
            LOG.trace("Shutting down service: {}", this);
            try (AutoCloseable ignored = doLifecycleChange()) {
                doShutdown();
                LOG.trace("Shutdown service: {}", this);
                status = SHUTDOWN;
            } catch (Exception e) {
                LOG.trace("Error shutting down service: " + this, e);
                fail(e);
            }
        }
    }

    public ServiceStatus getStatus() {
        switch (status) {
            case INITIALIZING:
                return ServiceStatus.Initializing;
            case INITIALIZED:
                return ServiceStatus.Initialized;
            case STARTING:
                return ServiceStatus.Starting;
            case STARTED:
                return ServiceStatus.Started;
            case SUSPENDING:
                return ServiceStatus.Suspending;
            case SUSPENDED:
                return ServiceStatus.Suspended;
            case STOPPING:
                return ServiceStatus.Stopping;
            default:
                return ServiceStatus.Stopped;
        }
    }

    public boolean isNew() {
        return status == NEW;
    }

    public boolean isBuild() {
        return status == BUILDED;
    }

    public boolean isInit() {
        return status == INITIALIZED;
    }

    public boolean isStarted() {
        return status == STARTED;
    }

    public boolean isStarting() {
        return status == STARTING;
    }

    public boolean isStopping() {
        return status == STOPPING;
    }

    public boolean isStopped() {
        return status < STARTING || status >= STOPPED;
    }

    public boolean isSuspending() {
        return status == SUSPENDING;
    }

    public boolean isSuspended() {
        return status == SUSPENDED;
    }

    public boolean isRunAllowed() {
        return status >= STARTING && status <= SUSPENDED;
    }

    public boolean isShutdown() {
        return status == SHUTDOWN;
    }

    /**
     * Is the service in progress of being stopped or already stopped
     */
    public boolean isStoppingOrStopped() {
        return status < STARTING || status > SUSPENDED;
    }

    /**
     * Is the service in progress of being suspended or already suspended
     */
    public boolean isSuspendingOrSuspended() {
        return status == SUSPENDING || status == SUSPENDED;
    }

    /**
     * Is the service in progress of being suspended or already suspended
     */
    public boolean isStartingOrStarted() {
        return status == STARTING || status == STARTED;
    }

    protected void fail(Exception e) {
        try {
            doFail(e);
        } finally {
            status = FAILED;
        }
    }

    /**
     * Optional build phase of the service.
     * This method will only be called by frameworks which supports pre-building projects such as camel-quarkus.
     */
    protected void doBuild() throws Exception {
        // noop
    }

    /**
     * Initialize the service.
     * This method will only be called once before starting.
     */
    protected void doInit() throws Exception {
        // noop
    }

    /**
     * Implementations override this method to support customized start/stop.
     * <p/>
     * <b>Important: </b> See {@link #doStop()} for more details.
     *
     * @see #doStop()
     */
    protected void doStart() throws Exception {
        // noop
    }

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
    protected void doStop() throws Exception {
        // noop
    }

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

    /**
     * Implementations override this method to perform any action upon failure.
     */
    protected void doFail(Exception e) {
        throw RuntimeCamelException.wrapRuntimeException(e);
    }

    /**
     * Implementations may return an object that will be closed
     * when the lifecycle action is completed.
     */
    protected AutoCloseable doLifecycleChange() {
        return null;
    }

}
