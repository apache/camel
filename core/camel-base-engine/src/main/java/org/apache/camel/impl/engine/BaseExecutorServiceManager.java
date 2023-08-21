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
package org.apache.camel.impl.engine;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NamedNode;
import org.apache.camel.StaticService;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultThreadPoolFactory;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.apache.camel.util.concurrent.SizedScheduledExecutorService;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base {@link org.apache.camel.spi.ExecutorServiceManager} which can be used for implementations
 */
public class BaseExecutorServiceManager extends ServiceSupport implements ExecutorServiceManager {
    private static final Logger LOG = LoggerFactory.getLogger(BaseExecutorServiceManager.class);

    private final CamelContext camelContext;
    private final List<ExecutorService> executorServices = new CopyOnWriteArrayList<>();
    private final Map<String, ThreadPoolProfile> threadPoolProfiles = new ConcurrentHashMap<>();
    private ThreadPoolFactory threadPoolFactory;
    private String threadNamePattern;
    private long shutdownAwaitTermination = 10000;
    private String defaultThreadPoolProfileId = "defaultThreadPoolProfile";
    private final ThreadPoolProfile defaultProfile;

    public BaseExecutorServiceManager(CamelContext camelContext) {
        this.camelContext = camelContext;

        defaultProfile = new ThreadPoolProfile(defaultThreadPoolProfileId);
        defaultProfile.setDefaultProfile(true);
        defaultProfile.setPoolSize(10);
        defaultProfile.setMaxPoolSize(20);
        defaultProfile.setKeepAliveTime(60L);
        defaultProfile.setTimeUnit(TimeUnit.SECONDS);
        defaultProfile.setMaxQueueSize(1000);
        defaultProfile.setAllowCoreThreadTimeOut(true);
        defaultProfile.setRejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns);

        registerThreadPoolProfile(defaultProfile);
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public ThreadPoolFactory getThreadPoolFactory() {
        return threadPoolFactory;
    }

    @Override
    public void setThreadPoolFactory(ThreadPoolFactory threadPoolFactory) {
        this.threadPoolFactory = threadPoolFactory;
    }

    @Override
    public void registerThreadPoolProfile(ThreadPoolProfile profile) {
        ObjectHelper.notNull(profile, "profile");
        StringHelper.notEmpty(profile.getId(), "id", profile);
        threadPoolProfiles.put(profile.getId(), profile);
    }

    @Override
    public ThreadPoolProfile getThreadPoolProfile(String id) {
        return threadPoolProfiles.get(id);
    }

    @Override
    public ThreadPoolProfile getDefaultThreadPoolProfile() {
        return getThreadPoolProfile(defaultThreadPoolProfileId);
    }

    @Override
    public void setDefaultThreadPoolProfile(ThreadPoolProfile defaultThreadPoolProfile) {
        threadPoolProfiles.remove(defaultThreadPoolProfileId);
        defaultThreadPoolProfile.addDefaults(defaultProfile);

        LOG.info("Using custom DefaultThreadPoolProfile: {}", defaultThreadPoolProfile);

        this.defaultThreadPoolProfileId = defaultThreadPoolProfile.getId();
        defaultThreadPoolProfile.setDefaultProfile(true);
        registerThreadPoolProfile(defaultThreadPoolProfile);
    }

    @Override
    public String getThreadNamePattern() {
        return threadNamePattern;
    }

    @Override
    public void setThreadNamePattern(String threadNamePattern) {
        // must set camel id here in the pattern and let the other placeholders be resolved on demand
        if (threadNamePattern != null) {
            this.threadNamePattern = threadNamePattern.replace("#camelId#", this.camelContext.getName());
        } else {
            this.threadNamePattern = threadNamePattern;
        }
    }

    @Override
    public long getShutdownAwaitTermination() {
        return shutdownAwaitTermination;
    }

    @Override
    public void setShutdownAwaitTermination(long shutdownAwaitTermination) {
        this.shutdownAwaitTermination = shutdownAwaitTermination;
    }

    @Override
    public String resolveThreadName(String name) {
        return ThreadHelper.resolveThreadName(threadNamePattern, name);
    }

    @Override
    public Thread newThread(String name, Runnable runnable) {
        ThreadFactory factory = createThreadFactory(name, true);
        return factory.newThread(runnable);
    }

    @Override
    public ExecutorService newDefaultThreadPool(Object source, String name) {
        return newThreadPool(source, name, getDefaultThreadPoolProfile());
    }

    @Override
    public ScheduledExecutorService newDefaultScheduledThreadPool(Object source, String name) {
        return newScheduledThreadPool(source, name, getDefaultThreadPoolProfile());
    }

    @Override
    public ExecutorService newThreadPool(Object source, String name, String profileId) {
        ThreadPoolProfile profile = getThreadPoolProfile(profileId);
        if (profile != null) {
            return newThreadPool(source, name, profile);
        } else {
            // no profile with that id
            return null;
        }
    }

    @Override
    public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
        String sanitizedName = URISupport.sanitizeUri(name);
        ObjectHelper.notNull(profile, "ThreadPoolProfile");

        ThreadPoolProfile defaultProfile = getDefaultThreadPoolProfile();
        profile.addDefaults(defaultProfile);

        ThreadFactory threadFactory = createThreadFactory(sanitizedName, true);
        ExecutorService executorService = threadPoolFactory.newThreadPool(profile, threadFactory);
        onThreadPoolCreated(executorService, source, profile.getId());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new ThreadPool for source: {} with name: {}. -> {}", source, sanitizedName, executorService);
        }

        return executorService;
    }

    @Override
    public ExecutorService newThreadPool(Object source, String name, int poolSize, int maxPoolSize) {
        ThreadPoolProfile profile = new ThreadPoolProfile(name);
        profile.setPoolSize(poolSize);
        profile.setMaxPoolSize(maxPoolSize);
        return newThreadPool(source, name, profile);
    }

    @Override
    public ExecutorService newSingleThreadExecutor(Object source, String name) {
        return newFixedThreadPool(source, name, 1);
    }

    @Override
    public ExecutorService newCachedThreadPool(Object source, String name) {
        String sanitizedName = URISupport.sanitizeUri(name);
        ExecutorService answer = threadPoolFactory.newCachedThreadPool(createThreadFactory(sanitizedName, true));
        onThreadPoolCreated(answer, source, null);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new CachedThreadPool for source: {} with name: {}. -> {}", source, sanitizedName, answer);
        }
        return answer;
    }

    @Override
    public ExecutorService newFixedThreadPool(Object source, String name, int poolSize) {
        ThreadPoolProfile profile = new ThreadPoolProfile(name);
        profile.setPoolSize(poolSize);
        profile.setMaxPoolSize(poolSize);
        profile.setKeepAliveTime(0L);
        profile.setAllowCoreThreadTimeOut(false);
        return newThreadPool(source, name, profile);
    }

    @Override
    public ScheduledExecutorService newSingleThreadScheduledExecutor(Object source, String name) {
        ThreadPoolProfile profile = new ThreadPoolProfile(name);
        profile.setPoolSize(1);
        profile.setAllowCoreThreadTimeOut(false);
        return newScheduledThreadPool(source, name, profile);
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(Object source, String name, ThreadPoolProfile profile) {
        String sanitizedName = URISupport.sanitizeUri(name);
        profile.addDefaults(getDefaultThreadPoolProfile());
        ScheduledExecutorService answer
                = threadPoolFactory.newScheduledThreadPool(profile, createThreadFactory(sanitizedName, true));
        onThreadPoolCreated(answer, source, null);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new ScheduledThreadPool for source: {} with name: {} -> {}", source, sanitizedName, answer);
        }
        return answer;
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(Object source, String name, String profileId) {
        ThreadPoolProfile profile = getThreadPoolProfile(profileId);
        if (profile != null) {
            return newScheduledThreadPool(source, name, profile);
        } else {
            // no profile with that id
            return null;
        }
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(Object source, String name, int poolSize) {
        ThreadPoolProfile profile = new ThreadPoolProfile(name);
        profile.setPoolSize(poolSize);
        return newScheduledThreadPool(source, name, profile);
    }

    @Override
    public void shutdown(ExecutorService executorService) {
        doShutdown(executorService, 0, false);
    }

    @Override
    public void shutdownGraceful(ExecutorService executorService) {
        doShutdown(executorService, getShutdownAwaitTermination(), false);
    }

    @Override
    public void shutdownGraceful(ExecutorService executorService, long shutdownAwaitTermination) {
        doShutdown(executorService, shutdownAwaitTermination, false);
    }

    private boolean doShutdown(ExecutorService executorService, long shutdownAwaitTermination, boolean failSafe) {
        if (executorService == null) {
            return false;
        }

        boolean warned = false;

        // shutting down a thread pool is a 2 step process. First we try graceful, and if that fails, then we go more aggressively
        // and try shutting down again. In both cases we wait at most the given shutdown timeout value given
        // (total wait could then be 2 x shutdownAwaitTermination, but when we shutdown the 2nd time we are aggressive and thus
        // we ought to shutdown much faster)
        if (!executorService.isShutdown()) {
            StopWatch watch = new StopWatch();

            LOG.trace("Shutdown of ExecutorService: {} with await termination: {} millis", executorService,
                    shutdownAwaitTermination);
            executorService.shutdown();

            if (shutdownAwaitTermination > 0) {
                try {
                    if (!awaitTermination(executorService, shutdownAwaitTermination)) {
                        warned = true;
                        LOG.warn("Forcing shutdown of ExecutorService: {} due first await termination elapsed.",
                                executorService);
                        executorService.shutdownNow();
                        // we are now shutting down aggressively, so wait to see if we can completely shutdown or not
                        if (!awaitTermination(executorService, shutdownAwaitTermination)) {
                            LOG.warn(
                                    "Cannot completely force shutdown of ExecutorService: {} due second await termination elapsed.",
                                    executorService);
                        }
                    }
                } catch (InterruptedException e) {
                    warned = true;
                    LOG.warn("Forcing shutdown of ExecutorService: {} due interrupted.", executorService);
                    // we were interrupted during shutdown, so force shutdown
                    executorService.shutdownNow();
                }
            }

            // if we logged at WARN level, then report at INFO level when we are complete so the end user can see this in the log
            if (warned) {
                LOG.info("Shutdown of ExecutorService: {} is shutdown: {} and terminated: {} took: {}.",
                        executorService, executorService.isShutdown(), executorService.isTerminated(),
                        TimeUtils.printDuration(watch.taken(), true));
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Shutdown of ExecutorService: {} is shutdown: {} and terminated: {} took: {}.",
                        executorService, executorService.isShutdown(), executorService.isTerminated(),
                        TimeUtils.printDuration(watch.taken(), true));
            }
        }

        doRemove(executorService, failSafe);

        return warned;
    }

    private void doRemove(ExecutorService executorService, boolean failSafe) {
        // let lifecycle strategy be notified as well which can let it be managed in JMX as well
        ThreadPoolExecutor threadPool = null;
        if (executorService instanceof ThreadPoolExecutor) {
            threadPool = (ThreadPoolExecutor) executorService;
        } else if (executorService instanceof SizedScheduledExecutorService) {
            threadPool = ((SizedScheduledExecutorService) executorService).getScheduledThreadPoolExecutor();
        }
        if (threadPool != null) {
            for (LifecycleStrategy lifecycle : camelContext.getLifecycleStrategies()) {
                lifecycle.onThreadPoolRemove(camelContext, threadPool);
            }
        }

        // remove reference as its shutdown (do not remove if fail-safe)
        if (!failSafe) {
            executorServices.remove(executorService);
        }
    }

    @Override
    public List<Runnable> shutdownNow(ExecutorService executorService) {
        return doShutdownNow(executorService, false);
    }

    private List<Runnable> doShutdownNow(ExecutorService executorService, boolean failSafe) {
        ObjectHelper.notNull(executorService, "executorService");

        List<Runnable> answer = null;
        if (!executorService.isShutdown()) {
            if (failSafe) {
                // log as warn, as we shutdown as fail-safe, so end user should see more details in the log.
                LOG.warn("Forcing shutdown of ExecutorService: {}", executorService);
            } else {
                LOG.debug("Forcing shutdown of ExecutorService: {}", executorService);
            }
            answer = executorService.shutdownNow();
            if (LOG.isTraceEnabled()) {
                LOG.trace("Shutdown of ExecutorService: {} is shutdown: {} and terminated: {}.",
                        executorService, executorService.isShutdown(), executorService.isTerminated());
            }
        }

        doRemove(executorService, failSafe);

        return answer;
    }

    @Override
    public boolean awaitTermination(ExecutorService executorService, long shutdownAwaitTermination)
            throws InterruptedException {
        // log progress every 2nd second so end user is aware of we are shutting down
        StopWatch watch = new StopWatch();
        long interval = Math.min(2000, shutdownAwaitTermination);
        boolean done = false;
        while (!done && interval > 0) {
            if (executorService.awaitTermination(interval, TimeUnit.MILLISECONDS)) {
                done = true;
            } else {
                LOG.info("Waited {} for ExecutorService: {} to terminate...", TimeUtils.printDuration(watch.taken(), true),
                        executorService);
                // recalculate interval
                interval = Math.min(2000, shutdownAwaitTermination - watch.taken());
            }
        }

        return done;
    }

    /**
     * Strategy callback when a new {@link java.util.concurrent.ExecutorService} have been created.
     *
     * @param executorService the created {@link java.util.concurrent.ExecutorService}
     */
    protected void onNewExecutorService(ExecutorService executorService) {
        // noop
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (threadNamePattern == null) {
            // set default name pattern which includes the camel context name
            threadNamePattern = "Camel (" + camelContext.getName() + ") thread ##counter# - #name#";
        }

        // discover thread pool factory
        if (threadPoolFactory == null) {
            threadPoolFactory = ResolverHelper.resolveService(
                    camelContext,
                    camelContext.getCamelContextExtension().getBootstrapFactoryFinder(),
                    ThreadPoolFactory.FACTORY,
                    ThreadPoolFactory.class)
                    .orElseGet(DefaultThreadPoolFactory::new);
        }
        CamelContextAware.trySetCamelContext(threadPoolFactory, camelContext);
        ServiceHelper.initService(threadPoolFactory);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(threadPoolFactory);
    }

    @Override
    protected void doShutdown() throws Exception {
        // shutdown all remainder executor services by looping and doing this aggressively
        // as by normal all threads pool should have been shutdown using proper lifecycle
        // by their EIPs, components etc. This is acting as a fail-safe during shutdown
        // of CamelContext itself.
        Set<ExecutorService> forced = new LinkedHashSet<>();
        if (!executorServices.isEmpty()) {
            // at first give a bit of time to shutdown nicely as the thread pool is most likely in the process of being shutdown also
            LOG.debug("Giving time for {} ExecutorService's to shutdown properly (acting as fail-safe)",
                    executorServices.size());
            for (ExecutorService executorService : executorServices) {
                try {
                    boolean warned = doShutdown(executorService, getShutdownAwaitTermination(), true);
                    // remember the thread pools that was forced to shutdown (eg warned)
                    if (warned) {
                        forced.add(executorService);
                    }
                } catch (Exception e) {
                    // only log if something goes wrong as we want to shutdown them all
                    LOG.warn("Error occurred during shutdown of ExecutorService: {}. This exception will be ignored.",
                            executorService, e);
                }
            }
        }

        // log the thread pools which was forced to shutdown so it may help the user to identify a problem of his
        if (!forced.isEmpty()) {
            LOG.warn("Forced shutdown of {} ExecutorService's which has not been shutdown properly (acting as fail-safe)",
                    forced.size());
            for (ExecutorService executorService : forced) {
                LOG.warn("  forced -> {}", executorService);
            }
        }
        forced.clear();

        // clear list
        executorServices.clear();

        // do not clear the default profile as we could potential be restarted
        Iterator<ThreadPoolProfile> it = threadPoolProfiles.values().iterator();
        while (it.hasNext()) {
            ThreadPoolProfile profile = it.next();
            if (!profile.isDefaultProfile()) {
                it.remove();
            }
        }

        ServiceHelper.stopAndShutdownServices(threadPoolFactory);
    }

    /**
     * Invoked when a new thread pool is created. This implementation will invoke the
     * {@link LifecycleStrategy#onThreadPoolAdd(org.apache.camel.CamelContext, java.util.concurrent.ThreadPoolExecutor, String, String, String, String)
     * LifecycleStrategy.onThreadPoolAdd} method, which for example will enlist the thread pool in JMX management.
     *
     * @param executorService     the thread pool
     * @param source              the source to use the thread pool
     * @param threadPoolProfileId profile id, if the thread pool was created from a thread pool profile
     */
    private void onThreadPoolCreated(ExecutorService executorService, Object source, String threadPoolProfileId) {
        // add to internal list of thread pools
        executorServices.add(executorService);

        String id;
        String sourceId = null;
        String routeId = null;

        // extract id from source
        if (source instanceof NamedNode) {
            id = ((NamedNode) source).getId();
            // and let source be the short name of the pattern
            sourceId = ((NamedNode) source).getShortName();
        } else if (source instanceof String) {
            id = (String) source;
        } else if (source != null) {
            if (source instanceof StaticService) {
                // the source is static service so its name would be unique
                id = source.getClass().getSimpleName();
            } else {
                // fallback and use the simple class name with hashcode for the id so its unique for this given source
                id = source.getClass().getSimpleName() + "(" + ObjectHelper.getIdentityHashCode(source) + ")";
            }
        } else {
            // no source, so fallback and use the simple class name from thread pool and its hashcode identity so its unique
            id = executorService.getClass().getSimpleName() + "(" + ObjectHelper.getIdentityHashCode(executorService) + ")";
        }

        // id is mandatory
        StringHelper.notEmpty(id, "id for thread pool " + executorService);

        // extract route id if possible
        if (source instanceof NamedNode) {
            routeId = CamelContextHelper.getRouteId((NamedNode) source);
        }

        // let lifecycle strategy be notified as well which can let it be managed in JMX as well
        ThreadPoolExecutor threadPool = null;
        if (executorService instanceof ThreadPoolExecutor) {
            threadPool = (ThreadPoolExecutor) executorService;
        } else if (executorService instanceof SizedScheduledExecutorService) {
            threadPool = ((SizedScheduledExecutorService) executorService).getScheduledThreadPoolExecutor();
        }
        if (threadPool != null) {
            for (LifecycleStrategy lifecycle : camelContext.getLifecycleStrategies()) {
                lifecycle.onThreadPoolAdd(camelContext, threadPool, id, sourceId, routeId, threadPoolProfileId);
            }
        }

        // now call strategy to allow custom logic
        onNewExecutorService(executorService);
    }

    protected ThreadFactory createThreadFactory(String name, boolean isDaemon) {
        return new CamelThreadFactory(threadNamePattern, name, isDaemon);
    }

}
