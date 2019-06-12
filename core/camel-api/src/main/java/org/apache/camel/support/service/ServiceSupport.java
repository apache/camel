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
import org.apache.camel.StatefulService;
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
public abstract class ServiceSupport implements StatefulService {

    protected static final int NEW = 0;
    protected static final int INITIALIZED = 1;
    protected static final int STARTING = 2;
    protected static final int STARTED = 3;
    protected static final int SUSPENDING = 4;
    protected static final int SUSPENDED = 5;
    protected static final int STOPPING = 6;
    protected static final int STOPPED = 7;
    protected static final int SHUTTINGDOWN = 8;
    protected static final int SHUTDOWN = 9;
    protected static final int FAILED = 10;

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final Object lock = new Object();
    protected volatile int status = NEW;

    public void init() {
        if (status == NEW) {
            synchronized (lock) {
                if (status == NEW) {
                    log.trace("Initializing service: {}", this);
                    try {
                        doInit();
                    } catch (Exception e) {
                        throw RuntimeCamelException.wrapRuntimeException(e);
                    }
                    status = INITIALIZED;
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
                log.trace("Service: {} already started", this);
                return;
            }
            if (status == STARTING) {
                log.trace("Service: {} already starting", this);
                return;
            }
            try {
                init();
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error while initializing service: " + this, e);
                throw e;
            }
            try {
                status = STARTING;
                log.trace("Starting service: {}", this);
                doStart();
                status = STARTED;
                log.trace("Service started: {}", this);
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error while starting service: " + this, e);
                throw RuntimeCamelException.wrapRuntimeException(e);
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
                status = STOPPED;
                log.trace("Service: {} stopped service", this);
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error while stopping service: " + this, e);
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
    }

    /**
     * <b>Important: </b> You should override the lifecycle methods that start with <tt>do</tt>, eg {@link #doStart()},
     * {@link #doStop()}, etc. where you implement your logic. The methods {@link #start()}, {@link #stop()} should
     * <b>NOT</b> be overridden as they are used internally to keep track of the state of this service and properly
     * invoke the operation in a safe manner.
     */
    @Override
    public void suspend() {
        synchronized (lock) {
            if (status == SUSPENDED) {
                log.trace("Service: {} already suspended", this);
                return;
            }
            if (status == SUSPENDING) {
                log.trace("Service: {} already suspending", this);
                return;
            }
            status = SUSPENDING;
            log.trace("Suspending service: {}", this);
            try {
                doSuspend();
                status = SUSPENDED;
                log.trace("Service suspended: {}", this);
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error while suspending service: " + this, e);
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
    }

    /**
     * <b>Important: </b> You should override the lifecycle methods that start with <tt>do</tt>, eg {@link #doStart()},
     * {@link #doStop()}, etc. where you implement your logic. The methods {@link #start()}, {@link #stop()} should
     * <b>NOT</b> be overridden as they are used internally to keep track of the state of this service and properly
     * invoke the operation in a safe manner.
     */
    @Override
    public void resume() {
        synchronized (lock) {
            if (status != SUSPENDED) {
                log.trace("Service is not suspended: {}", this);
                return;
            }
            status = STARTING;
            log.trace("Resuming service: {}", this);
            try {
                doResume();
                status = STARTED;
                log.trace("Service resumed: {}", this);
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error while resuming service: " + this, e);
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
    }

    /**
     * <b>Important: </b> You should override the lifecycle methods that start with <tt>do</tt>, eg {@link #doStart()},
     * {@link #doStop()}, etc. where you implement your logic. The methods {@link #start()}, {@link #stop()} should
     * <b>NOT</b> be overridden as they are used internally to keep track of the state of this service and properly
     * invoke the operation in a safe manner.
     */
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
                log.trace("Service: {} shut down", this);
                status = SHUTDOWN;
            } catch (Exception e) {
                status = FAILED;
                log.trace("Error shutting down service: " + this, e);
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
    }

    @Override
    public ServiceStatus getStatus() {
        switch (status) {
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

    public boolean isInit() {
        return status == INITIALIZED;
    }

    @Override
    public boolean isStarted() {
        return status == STARTED;
    }

    @Override
    public boolean isStarting() {
        return status == STARTING;
    }

    @Override
    public boolean isStopping() {
        return status == STOPPING;
    }

    @Override
    public boolean isStopped() {
        return status == NEW || status == INITIALIZED || status == STOPPED || status == SHUTTINGDOWN || status == SHUTDOWN || status == FAILED;
    }

    @Override
    public boolean isSuspending() {
        return status == SUSPENDING;
    }

    @Override
    public boolean isSuspended() {
        return status == SUSPENDED;
    }

    @Override
    public boolean isRunAllowed() {
        return isStartingOrStarted() || isSuspendingOrSuspended();
    }

    public boolean isShutdown() {
        return status == SHUTDOWN;
    }

    /**
     * Is the service in progress of being stopped or already stopped
     */
    public boolean isStoppingOrStopped() {
        return isStopping() || isStopped();
    }

    /**
     * Is the service in progress of being suspended or already suspended
     */
    public boolean isSuspendingOrSuspended() {
        return isSuspending() || isSuspended();
    }

    /**
     * Is the service in progress of being suspended or already suspended
     */
    public boolean isStartingOrStarted() {
        return isStarting() || isStarted();
    }

    /**
     * Initialize the service.
     * This method will only be called once before starting.
     */
    protected void doInit() throws Exception {
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

}
