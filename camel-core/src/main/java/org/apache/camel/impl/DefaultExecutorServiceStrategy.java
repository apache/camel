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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.ExecutorServiceStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class DefaultExecutorServiceStrategy extends ServiceSupport implements ExecutorServiceStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultExecutorServiceStrategy.class);
    private final List<ExecutorService> executorServices = new ArrayList<ExecutorService>();
    private final CamelContext camelContext;
    private String threadNamePattern;
    private String defaultThreadPoolProfileId;
    private final Map<String, ThreadPoolProfile> threadPoolProfiles = new HashMap<String, ThreadPoolProfile>();

    public DefaultExecutorServiceStrategy(CamelContext camelContext) {
        this.camelContext = camelContext;

        // create and register the default profile
        this.defaultThreadPoolProfileId = "defaultThreadPoolProfile";
        ThreadPoolProfile defaultProfile = new ThreadPoolProfileSupport(defaultThreadPoolProfileId);
        // the default profile has the following values
        defaultProfile.setDefaultProfile(true);
        defaultProfile.setPoolSize(10);
        defaultProfile.setMaxPoolSize(20);
        defaultProfile.setKeepAliveTime(60L);
        defaultProfile.setTimeUnit(TimeUnit.SECONDS);
        defaultProfile.setMaxQueueSize(1000);
        defaultProfile.setRejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns);
        registerThreadPoolProfile(defaultProfile);
    }

    public void registerThreadPoolProfile(ThreadPoolProfile profile) {
        threadPoolProfiles.put(profile.getId(), profile);
    }

    public ThreadPoolProfile getThreadPoolProfile(String id) {
        return threadPoolProfiles.get(id);
    }

    public ThreadPoolProfile getDefaultThreadPoolProfile() {
        return getThreadPoolProfile(defaultThreadPoolProfileId);
    }

    public void setDefaultThreadPoolProfile(ThreadPoolProfile defaultThreadPoolProfile) {
        ThreadPoolProfile oldProfile = threadPoolProfiles.remove(defaultThreadPoolProfileId);
        if (oldProfile != null) {
            // the old is no longer default
            oldProfile.setDefaultProfile(false);

            // fallback and use old default values for new default profile if absent (convention over configuration)
            if (defaultThreadPoolProfile.getKeepAliveTime() == null) {
                defaultThreadPoolProfile.setKeepAliveTime(oldProfile.getKeepAliveTime());
            }
            if (defaultThreadPoolProfile.getMaxPoolSize() == null) {
                defaultThreadPoolProfile.setMaxPoolSize(oldProfile.getMaxPoolSize());
            }
            if (defaultThreadPoolProfile.getRejectedPolicy() == null) {
                defaultThreadPoolProfile.setRejectedPolicy(oldProfile.getRejectedPolicy());
            }
            if (defaultThreadPoolProfile.getMaxQueueSize() == null) {
                defaultThreadPoolProfile.setMaxQueueSize(oldProfile.getMaxQueueSize());
            }
            if (defaultThreadPoolProfile.getPoolSize() == null) {
                defaultThreadPoolProfile.setPoolSize(oldProfile.getPoolSize());
            }
            if (defaultThreadPoolProfile.getTimeUnit() == null) {
                defaultThreadPoolProfile.setTimeUnit(oldProfile.getTimeUnit());
            }
        }

        // validate that all options has been given as its mandatory for a default thread pool profile
        // as it is used as fallback for other profiles if they do not have that particular value
        ObjectHelper.notEmpty(defaultThreadPoolProfile.getId(), "id", defaultThreadPoolProfile);
        ObjectHelper.notNull(defaultThreadPoolProfile.getKeepAliveTime(), "keepAliveTime", defaultThreadPoolProfile);
        ObjectHelper.notNull(defaultThreadPoolProfile.getMaxPoolSize(), "maxPoolSize", defaultThreadPoolProfile);
        ObjectHelper.notNull(defaultThreadPoolProfile.getMaxQueueSize(), "maxQueueSize", defaultThreadPoolProfile);
        ObjectHelper.notNull(defaultThreadPoolProfile.getPoolSize(), "poolSize", defaultThreadPoolProfile);
        ObjectHelper.notNull(defaultThreadPoolProfile.getTimeUnit(), "timeUnit", defaultThreadPoolProfile);

        LOG.info("Using custom DefaultThreadPoolProfile: " + defaultThreadPoolProfile);

        // and replace with the new default profile
        this.defaultThreadPoolProfileId = defaultThreadPoolProfile.getId();
        // and mark the new profile as default
        defaultThreadPoolProfile.setDefaultProfile(true);
        registerThreadPoolProfile(defaultThreadPoolProfile);
    }

    public String getThreadName(String name) {
        return ExecutorServiceHelper.getThreadName(threadNamePattern, name);
    }

    public String getThreadNamePattern() {
        return threadNamePattern;
    }

    public void setThreadNamePattern(String threadNamePattern) {
        // must set camel id here in the pattern and let the other placeholders be resolved by ExecutorServiceHelper
        String name = threadNamePattern.replaceFirst("\\$\\{camelId\\}", camelContext.getName());
        this.threadNamePattern = name;
    }

    public ExecutorService lookup(Object source, String name, String executorServiceRef) {
        ExecutorService answer = camelContext.getRegistry().lookup(executorServiceRef, ExecutorService.class);
        if (answer != null) {
            LOG.debug("Looking up ExecutorService with ref: {} and found it from Registry: {}", executorServiceRef, answer);
        }

        if (answer == null) {
            // try to see if we got a thread pool profile with that id
            answer = newThreadPool(source, name, executorServiceRef);
            if (answer != null) {
                LOG.debug("Looking up ExecutorService with ref: {} and found a matching ThreadPoolProfile to create the ExecutorService: {}",
                        executorServiceRef, answer);
            }
        }

        return answer;
    }

    public ScheduledExecutorService lookupScheduled(Object source, String name, String executorServiceRef) {
        ScheduledExecutorService answer = camelContext.getRegistry().lookup(executorServiceRef, ScheduledExecutorService.class);
        if (answer != null) {
            LOG.debug("Looking up ScheduledExecutorService with ref: {} and found it from Registry: {}", executorServiceRef, answer);
        }

        if (answer == null) {
            ThreadPoolProfile profile = getThreadPoolProfile(name);
            if (profile != null) {
                int poolSize = profile.getPoolSize();
                answer = newScheduledThreadPool(source, name, poolSize);
                if (answer != null) {
                    LOG.debug("Looking up ScheduledExecutorService with ref: {} and found a matching ThreadPoolProfile to create the ScheduledExecutorService: {}",
                            executorServiceRef, answer);
                }
            }
        }

        return answer;
    }

    public ExecutorService newDefaultThreadPool(Object source, String name) {
        ThreadPoolProfile profile = getDefaultThreadPoolProfile();
        ObjectHelper.notNull(profile, "DefaultThreadPoolProfile");

        return newThreadPool(source, name,
            profile.getPoolSize(), profile.getMaxPoolSize(),
            profile.getKeepAliveTime(), profile.getTimeUnit(),
            profile.getMaxQueueSize(), profile.getRejectedExecutionHandler(), false);
    }

    public ExecutorService newThreadPool(Object source, String name, String threadPoolProfileId) {
        ThreadPoolProfile defaultProfile = getDefaultThreadPoolProfile();
        ThreadPoolProfile profile = getThreadPoolProfile(threadPoolProfileId);
        if (profile != null) {
            // fallback to use values from default profile if not specified
            Integer poolSize = profile.getPoolSize() != null ? profile.getPoolSize() : defaultProfile.getPoolSize();
            Integer maxPoolSize = profile.getMaxPoolSize() != null ? profile.getMaxPoolSize() : defaultProfile.getMaxPoolSize();
            Long keepAliveTime = profile.getKeepAliveTime() != null ? profile.getKeepAliveTime() : defaultProfile.getKeepAliveTime();
            TimeUnit timeUnit = profile.getTimeUnit() != null ? profile.getTimeUnit() : defaultProfile.getTimeUnit();
            Integer maxQueueSize = profile.getMaxQueueSize() != null ? profile.getMaxQueueSize() : defaultProfile.getMaxQueueSize();
            RejectedExecutionHandler handler = profile.getRejectedExecutionHandler() != null ? profile.getRejectedExecutionHandler() : defaultProfile.getRejectedExecutionHandler();
            // create the pool
            return newThreadPool(threadPoolProfileId, source, name, poolSize, maxPoolSize, keepAliveTime, timeUnit, maxQueueSize, handler, false);
        } else {
            // no profile with that id
            return null;
        }
    }

    public ExecutorService newCachedThreadPool(Object source, String name) {
        ExecutorService answer = ExecutorServiceHelper.newCachedThreadPool(threadNamePattern, name, true);
        onThreadPoolCreated(answer, source, null);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new cached thread pool for source: {} with name: {}. -> {}", new Object[]{source, name, answer});
        }
        return answer;
    }

    public ScheduledExecutorService newScheduledThreadPool(Object source, String name) {
        int poolSize = getDefaultThreadPoolProfile().getPoolSize();
        return newScheduledThreadPool(source, name, poolSize);
    }

    public ScheduledExecutorService newScheduledThreadPool(Object source, String name, int poolSize) {
        ScheduledExecutorService answer = ExecutorServiceHelper.newScheduledThreadPool(poolSize, threadNamePattern, name, true);
        onThreadPoolCreated(answer, source, null);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new scheduled thread pool for source: {} with name: {}. [poolSize={}]. -> {}", new Object[]{source, name, poolSize, answer});
        }
        return answer;
    }

    public ExecutorService newFixedThreadPool(Object source, String name, int poolSize) {
        ExecutorService answer = ExecutorServiceHelper.newFixedThreadPool(poolSize, threadNamePattern, name, true);
        onThreadPoolCreated(answer, source, null);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new fixed thread pool for source: {} with name: {}. [poolSize={}]. -> {}", new Object[]{source, name, poolSize, answer});
        }
        return answer;
    }

    public ExecutorService newSingleThreadExecutor(Object source, String name) {
        ExecutorService answer = ExecutorServiceHelper.newSingleThreadExecutor(threadNamePattern, name, true);
        onThreadPoolCreated(answer, source, null);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new single thread pool for source: {} with name: {}. -> {}", new Object[]{source, name, answer});
        }
        return answer;
    }

    public ExecutorService newSynchronousThreadPool(Object source, String name) {
        ExecutorService answer = ExecutorServiceHelper.newSynchronousThreadPool();
        onThreadPoolCreated(answer, source, null);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new synchronous thread pool for source: {} with name: {}. -> {}", new Object[]{source, name, answer});
        }
        return answer;
    }

    public ExecutorService newThreadPool(Object source, String name, int corePoolSize, int maxPoolSize) {
        ExecutorService answer = ExecutorServiceHelper.newThreadPool(threadNamePattern, name, corePoolSize, maxPoolSize);
        onThreadPoolCreated(answer, source, null);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new thread pool for source: {} with name: {}. [poolSize={}, maxPoolSize={}] -> {}",
                    new Object[]{source, name, corePoolSize, maxPoolSize, answer});
        }
        return answer;
    }

    public ExecutorService newThreadPool(Object source, String name, int corePoolSize, int maxPoolSize, int maxQueueSize) {
        ExecutorService answer = ExecutorServiceHelper.newThreadPool(threadNamePattern, name, corePoolSize, maxPoolSize, maxQueueSize);
        onThreadPoolCreated(answer, source, null);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new thread pool for source: {} with name: {}. [poolSize={}, maxPoolSize={}, maxQueueSize={}] -> {}",
                    new Object[]{source, name, corePoolSize, maxPoolSize, maxQueueSize, answer});
        }
        return answer;
    }

    private ExecutorService newThreadPool(String threadPoolProfileId, Object source, String name, int corePoolSize, int maxPoolSize, long keepAliveTime,
                                         TimeUnit timeUnit, int maxQueueSize, RejectedExecutionHandler rejectedExecutionHandler,
                                         boolean daemon) {

        // the thread name must not be null
        ObjectHelper.notNull(name, "ThreadName");

        // If we set the corePoolSize to be 0, the whole camel application will hang in JDK5
        // just add a check here to throw the IllegalArgumentException
        if (corePoolSize < 1) {
            throw new IllegalArgumentException("The corePoolSize can't be lower than 1");
        }

        ExecutorService answer = ExecutorServiceHelper.newThreadPool(threadNamePattern, name, corePoolSize, maxPoolSize, keepAliveTime,
                                                                     timeUnit, maxQueueSize, rejectedExecutionHandler, daemon);
        onThreadPoolCreated(answer, source, threadPoolProfileId);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new thread pool for source: {} with name: {}. [poolSize={}, maxPoolSize={}, keepAliveTime={} {}, maxQueueSize={}, "
                    + "rejectedExecutionHandler={}, daemon={}] -> {}",
                    new Object[]{source, name, corePoolSize, maxPoolSize, keepAliveTime, timeUnit, maxQueueSize, rejectedExecutionHandler, daemon, answer});
        }
        return answer;
    }


    public ExecutorService newThreadPool(Object source, String name, int corePoolSize, int maxPoolSize, long keepAliveTime,
                                         TimeUnit timeUnit, int maxQueueSize, RejectedExecutionHandler rejectedExecutionHandler,
                                         boolean daemon) {
        return newThreadPool(null, source, name, corePoolSize, maxPoolSize, keepAliveTime, timeUnit, maxQueueSize, rejectedExecutionHandler, daemon);
    }

    public void shutdown(ExecutorService executorService) {
        ObjectHelper.notNull(executorService, "executorService");

        if (executorService.isShutdown()) {
            return;
        }

        LOG.debug("Shutdown ExecutorService: {}", executorService);
        executorService.shutdown();
        LOG.trace("Shutdown ExecutorService: {} complete.", executorService);
    }

    public List<Runnable> shutdownNow(ExecutorService executorService) {
        ObjectHelper.notNull(executorService, "executorService");

        if (executorService.isShutdown()) {
            return null;
        }

        LOG.debug("ShutdownNow ExecutorService: {}", executorService);
        List<Runnable> answer = executorService.shutdownNow();
        LOG.trace("ShutdownNow ExecutorService: {} complete.", executorService);

        return answer;
    }

    private void onThreadPoolCreated(ExecutorService executorService, Object source, String threadPoolProfileId) {
        // add to internal list of thread pools
        executorServices.add(executorService);

        String id;
        String sourceId = null;
        String routeId = null;

        // extract id from source
        if (source instanceof OptionalIdentifiedDefinition) {
            id = ((OptionalIdentifiedDefinition) source).idOrCreate(camelContext.getNodeIdFactory());
            // and let source be the short name of the pattern
            sourceId = ((OptionalIdentifiedDefinition) source).getShortName();
        } else if (source instanceof String) {
            id = (String) source;
        } else if (source != null) {
            // fallback and use the simple class name with hashcode for the id so its unique for this given source
            id = source.getClass().getSimpleName() + "(" + ObjectHelper.getIdentityHashCode(source) + ")";
        } else {
            // no source, so fallback and use the simple class name from thread pool and its hashcode identity so its unique
            id = executorService.getClass().getSimpleName() + "(" + ObjectHelper.getIdentityHashCode(executorService) + ")";
        }

        // id is mandatory
        ObjectHelper.notEmpty(id, "id for thread pool " + executorService);

        // extract route id if possible
        if (source instanceof ProcessorDefinition) {
            RouteDefinition route = ProcessorDefinitionHelper.getRoute((ProcessorDefinition) source);
            if (route != null) {
                routeId = route.idOrCreate(camelContext.getNodeIdFactory());
            }
        }

        // let lifecycle strategy be notified as well which can let it be managed in JMX as well
        if (executorService instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executorService;
            for (LifecycleStrategy lifecycle : camelContext.getLifecycleStrategies()) {
                lifecycle.onThreadPoolAdd(camelContext, threadPool, id, sourceId, routeId, threadPoolProfileId);
            }
        }

        // now call strategy to allow custom logic
        onNewExecutorService(executorService);
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
    protected void doStart() throws Exception {
        if (threadNamePattern == null) {
            // set default name pattern which includes the camel context name
            threadNamePattern = "Camel (" + camelContext.getName() + ") thread #${counter} - ${name}";
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    @Override
    protected void doShutdown() throws Exception {
        // shutdown all executor services
        for (ExecutorService executorService : executorServices) {
            // only log if something goes wrong as we want to shutdown them all
            try {
                shutdownNow(executorService);
            } catch (Throwable e) {
                LOG.warn("Error occurred during shutdown of ExecutorService: "
                        + executorService + ". This exception will be ignored.", e);
            }
        }
        executorServices.clear();

        // do not clear the default profile as we could potential be restarted
        Iterator<ThreadPoolProfile> it = threadPoolProfiles.values().iterator();
        while (it.hasNext()) {
            ThreadPoolProfile profile = it.next();
            if (!profile.isDefaultProfile()) {
                it.remove();
            }
        }
    }

}
