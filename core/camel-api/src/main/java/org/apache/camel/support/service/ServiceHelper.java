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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.Channel;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.ShutdownableService;
import org.apache.camel.StatefulService;
import org.apache.camel.Suspendable;
import org.apache.camel.SuspendableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A collection of helper methods for working with {@link Service} objects.
 */
public final class ServiceHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceHelper.class);

    /**
     * Utility classes should not have a public constructor.
     */
    private ServiceHelper() {
    }

    /**
     * Builds the given {@code value} if it's a {@link Service} or a collection of it.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     */
    public static void buildService(Object value) {
        if (value instanceof Service service) {
            service.build();
        } else if (value instanceof Iterable iterable) {
            for (Object o : iterable) {
                buildService(o);
            }
        }
    }

    /**
     * Builds each element of the given {@code services} if {@code services} itself is not {@code null}, otherwise this
     * method would return immediately.
     *
     * @see #buildService(Object)
     */
    public static void buildService(Object... services) {
        if (services != null) {
            for (Object o : services) {
                buildService(o);
            }
        }
    }

    /**
     * Initializes the given {@code value} if it's a {@link Service} or a collection of it.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     */
    public static void initService(Object value) {
        if (value instanceof Service service) {
            service.init();
        } else if (value instanceof Iterable iterable) {
            for (Object o : iterable) {
                initService(o);
            }
        }
    }

    /**
     * Initializes each element of the given {@code services} if {@code services} itself is not {@code null}, otherwise
     * this method would return immediately.
     *
     * @see #initService(Object)
     */
    public static void initService(Object... services) {
        if (services != null) {
            for (Object o : services) {
                initService(o);
            }
        }
    }

    /**
     * Starts the given {@code value} if it's a {@link Service} or a collection of it.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     */
    public static void startService(Object value) {
        if (value instanceof Service service) {
            startService(service);
        } else if (value instanceof Iterable iterable) {
            startService(iterable);
        }
    }

    /**
     * Starts the given {@code value} if it's a {@link Service} or a collection of it.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     */
    public static void startService(Service service) {
        if (service != null) {
            service.start();
        }
    }

    /**
     * Starts the given {@code value} if it's a {@link Service} or a collection of it.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     */
    public static void startService(Iterable<?> value) {
        if (value != null) {
            for (Object o : value) {
                startService(o);
            }
        }
    }

    /**
     * Starts each element of the given {@code services} if {@code services} itself is not {@code null}, otherwise this
     * method would return immediately.
     *
     * @see #startService(Object)
     */
    public static void startService(Object... services) {
        if (services != null) {
            for (Object o : services) {
                startService(o);
            }
        }
    }

    /**
     * Stops each element of the given {@code services} if {@code services} itself is not {@code null}, otherwise this
     * method would return immediately.
     * <p/>
     * If there's any exception being thrown while stopping the elements one after the other this method would rethrow
     * the <b>first</b> such exception being thrown.
     *
     * @see #stopService(Collection)
     */
    public static void stopService(Object... services) {
        if (services != null) {
            for (Object o : services) {
                stopService(o);
            }
        }
    }

    /**
     * Stops the given {@code value}, rethrowing the first exception caught.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     *
     * @see Service#stop()
     * @see #stopService(Collection)
     */
    public static void stopService(Object value) {
        if (value instanceof Service service) {
            stopService(service);
        } else if (value instanceof Iterable iterable) {
            stopService(iterable);
        }
    }

    /**
     * Stops the given {@code value}, rethrowing the first exception caught.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     *
     * @see Service#stop()
     * @see #stopService(Collection)
     */
    public static void stopService(Service service) {
        if (service != null) {
            service.stop();
        }
    }

    /**
     * Stops the given {@code value}, rethrowing the first exception caught.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     *
     * @see Service#stop()
     * @see #stopService(Collection)
     */
    public static void stopService(Iterable<?> value) {
        if (value != null) {
            for (Object o : value) {
                stopService(o);
            }
        }
    }

    /**
     * Stops each element of the given {@code services} if {@code services} itself is not {@code null}, otherwise this
     * method would return immediately.
     * <p/>
     * If there's any exception being thrown while stopping the elements one after the other this method would rethrow
     * the <b>first</b> such exception being thrown.
     *
     * @see #stopService(Object)
     */
    public static void stopService(Collection<?> services) {
        if (services == null) {
            return;
        }
        RuntimeException firstException = null;
        for (Object value : services) {
            try {
                stopService(value);
            } catch (RuntimeException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Caught exception stopping service: {}", value, e);
                }
                if (firstException == null) {
                    firstException = e;
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    /**
     * Stops and shutdowns each element of the given {@code services} if {@code services} itself is not {@code null},
     * otherwise this method would return immediately.
     * <p/>
     * If there's any exception being thrown while stopping/shutting down the elements one after the other this method
     * would rethrow the <b>first</b> such exception being thrown.
     *
     * @see #stopAndShutdownServices(Collection)
     */
    public static void stopAndShutdownServices(Object... services) {
        if (services == null) {
            return;
        }
        List<Object> list = Arrays.asList(services);
        stopAndShutdownServices(list);
    }

    /**
     * Stops and shutdowns the given {@code service}, rethrowing the first exception caught.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     *
     * @see #stopService(Object)
     * @see ShutdownableService#shutdown()
     */
    public static void stopAndShutdownService(Object value) {
        stopService(value);

        // then try to shutdown
        if (value instanceof ShutdownableService service) {
            LOG.trace("Shutting down service {}", service);
            service.shutdown();
        }
    }

    /**
     * Stops and shutdowns the given {@code service}, rethrowing the first exception caught.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     *
     * @see #stopService(Object)
     * @see ShutdownableService#shutdown()
     */
    public static void stopAndShutdownService(ShutdownableService service) {
        stopService(service);

        if (service != null) {
            LOG.trace("Shutting down service {}", service);
            service.shutdown();
        }
    }

    /**
     * Stops and shutdowns each element of the given {@code services} if {@code services} itself is not {@code null},
     * otherwise this method would return immediately.
     * <p/>
     * If there's any exception being thrown while stopping/shutting down the elements one after the other this method
     * would rethrow the <b>first</b> such exception being thrown.
     *
     * @see #stopService(Object)
     * @see ShutdownableService#shutdown()
     */
    public static void stopAndShutdownServices(Collection<?> services) {
        if (services == null) {
            return;
        }
        RuntimeException firstException = null;

        for (Object value : services) {

            try {
                stopAndShutdownService(value);
            } catch (RuntimeException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Caught exception shutting down service: {}", value, e);
                }
                if (firstException == null) {
                    firstException = e;
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    /**
     * Resumes each element of the given {@code services} if {@code services} itself is not {@code null}, otherwise this
     * method would return immediately.
     * <p/>
     * If there's any exception being thrown while resuming the elements one after the other this method would rethrow
     * the <b>first</b> such exception being thrown.
     *
     * @see #resumeService(Object)
     */
    public static void resumeServices(Collection<?> services) {
        if (services == null) {
            return;
        }
        RuntimeException firstException = null;
        for (Object value : services) {
            if (value instanceof Service service) {
                try {
                    resumeService(service);
                } catch (RuntimeException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Caught exception resuming service: {}", service, e);
                    }
                    if (firstException == null) {
                        firstException = e;
                    }
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    /**
     * Resumes the given {@code service}.
     * <p/>
     * If {@code service} is both {@link org.apache.camel.Suspendable} and {@link org.apache.camel.SuspendableService}
     * then its {@link org.apache.camel.SuspendableService#resume()} is called but <b>only</b> if {@code service} is
     * already {@link #isSuspended(Object) suspended}.
     * <p/>
     * If {@code service} is <b>not</b> a {@link org.apache.camel.Suspendable} and
     * {@link org.apache.camel.SuspendableService} then its {@link org.apache.camel.Service#start()} is called.
     * <p/>
     * Calling this method has no effect if {@code service} is {@code null}.
     *
     * @param  service   the service
     * @return           <tt>true</tt> if either <tt>resume</tt> method or {@link #startService(Object)} was called,
     *                   <tt>false</tt> otherwise.
     * @throws Exception is thrown if error occurred
     * @see              #startService(Object)
     */
    public static boolean resumeService(Object service) {
        if (service instanceof Suspendable && service instanceof SuspendableService ss) {
            if (ss.isSuspended()) {
                LOG.debug("Resuming service {}", service);
                ss.resume();
                return true;
            } else {
                return false;
            }
        } else {
            startService(service);
            return true;
        }
    }

    /**
     * Suspends each element of the given {@code services} if {@code services} itself is not {@code null}, otherwise
     * this method would return immediately.
     * <p/>
     * If there's any exception being thrown while suspending the elements one after the other this method would rethrow
     * the <b>first</b> such exception being thrown.
     *
     * @see #suspendService(Object)
     */
    public static void suspendServices(Collection<?> services) {
        if (services == null) {
            return;
        }
        RuntimeException firstException = null;
        for (Object value : services) {
            if (value instanceof Service service) {
                try {
                    suspendService(service);
                } catch (RuntimeException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Caught exception suspending service: {}", service, e);
                    }
                    if (firstException == null) {
                        firstException = e;
                    }
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    /**
     * Suspends the given {@code service}.
     * <p/>
     * If {@code service} is both {@link org.apache.camel.Suspendable} and {@link org.apache.camel.SuspendableService}
     * then its {@link org.apache.camel.SuspendableService#suspend()} is called but <b>only</b> if {@code service} is
     * <b>not</b> already {@link #isSuspended(Object) suspended}.
     * <p/>
     * If {@code service} is <b>not</b> a {@link org.apache.camel.Suspendable} and
     * {@link org.apache.camel.SuspendableService} then its {@link org.apache.camel.Service#stop()} is called.
     * <p/>
     * Calling this method has no effect if {@code service} is {@code null}.
     *
     * @param  service   the service
     * @return           <tt>true</tt> if either the <tt>suspend</tt> method or {@link #stopService(Object)} was called,
     *                   <tt>false</tt> otherwise.
     * @throws Exception is thrown if error occurred
     * @see              #stopService(Object)
     */
    public static boolean suspendService(Object service) {
        if (service instanceof Suspendable && service instanceof SuspendableService ss) {
            if (!ss.isSuspended()) {
                LOG.trace("Suspending service {}", service);
                ss.suspend();
                return true;
            } else {
                return false;
            }
        } else {
            stopService(service);
            return true;
        }
    }

    /**
     * Is the given service stopping or already stopped?
     *
     * @return <tt>true</tt> if stopping or already stopped, <tt>false</tt> otherwise
     * @see    StatefulService#isStopping()
     * @see    StatefulService#isStopped()
     */
    public static boolean isStopped(Object value) {
        if (value instanceof StatefulService statefulService) {
            return isStopped(statefulService);
        }

        return false;
    }

    /**
     * Is the given service stopping or already stopped?
     *
     * @return <tt>true</tt> if stopping or already stopped, <tt>false</tt> otherwise
     * @see    StatefulService#isStopping()
     * @see    StatefulService#isStopped()
     */
    public static boolean isStopped(StatefulService service) {
        if (service != null && (service.isStopping() || service.isStopped())) {
            return true;
        }

        return false;
    }

    /**
     * Is the given service starting or already started?
     *
     * @return <tt>true</tt> if starting or already started, <tt>false</tt> otherwise
     * @see    StatefulService#isStarting()
     * @see    StatefulService#isStarted()
     */
    public static boolean isStarted(Object value) {
        if (value instanceof StatefulService statefulService) {
            return isStarted(statefulService);
        }

        return false;
    }

    /**
     * Is the given service starting or already started?
     *
     * @return <tt>true</tt> if starting or already started, <tt>false</tt> otherwise
     * @see    StatefulService#isStarting()
     * @see    StatefulService#isStarted()
     */
    public static boolean isStarted(StatefulService service) {
        if (service != null && (service.isStarting() || service.isStarted())) {
            return true;
        }

        return false;
    }

    /**
     * Is the given service suspending or already suspended?
     *
     * @return <tt>true</tt> if suspending or already suspended, <tt>false</tt> otherwise
     * @see    StatefulService#isSuspending()
     * @see    StatefulService#isSuspended()
     */
    public static boolean isSuspended(Object value) {
        if (value instanceof StatefulService statefulService) {
            return isSuspended(statefulService);
        }

        return false;
    }

    /**
     * Is the given service suspending or already suspended?
     *
     * @return <tt>true</tt> if suspending or already suspended, <tt>false</tt> otherwise
     * @see    StatefulService#isSuspending()
     * @see    StatefulService#isSuspended()
     */
    public static boolean isSuspended(StatefulService service) {
        if (service != null && (service.isSuspending() || service.isSuspended())) {
            return true;
        }

        return false;
    }

    /**
     * Gathers all child services by navigating the service to recursively gather all child services.
     * <p/>
     * The returned set does <b>not</b> include the children being error handler.
     *
     * @param  service the service
     * @return         the services, including the parent service, and all its children
     */
    public static Set<Service> getChildServices(Service service) {
        return getChildServices(service, false);
    }

    /**
     * Gathers all child services by navigating the service to recursively gather all child services.
     *
     * @param  service             the service
     * @param  includeErrorHandler whether to include error handlers
     * @return                     the services, including the parent service, and all its children
     */
    public static Set<Service> getChildServices(Service service, boolean includeErrorHandler) {
        Set<Service> answer = new LinkedHashSet<>();
        doGetChildServices(answer, service, includeErrorHandler);
        return answer;
    }

    private static void doGetChildServices(Set<Service> services, Service service, boolean includeErrorHandler) {
        services.add(service);
        if (service instanceof Navigate nav) {
            if (nav.hasNext()) {
                List<?> children = nav.next();
                for (Object child : children) {
                    if (child instanceof Channel channel) {
                        if (includeErrorHandler) {
                            // special for error handler as they are tied to the Channel
                            Processor errorHandler = channel.getErrorHandler();
                            if (errorHandler instanceof Service errService) {
                                services.add(errService);
                            }
                        }
                        Processor next = channel.getNextProcessor();
                        if (next instanceof Service nextService) {
                            services.add(nextService);
                        }
                    }
                    if (child instanceof Service childService) {
                        doGetChildServices(services, childService, includeErrorHandler);
                    }
                }
            }
        }
    }

}
