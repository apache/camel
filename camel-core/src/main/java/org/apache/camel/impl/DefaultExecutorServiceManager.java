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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.apache.camel.util.concurrent.SynchronousExecutorService;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class DefaultExecutorServiceManager extends ServiceSupport implements ExecutorServiceManager {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultExecutorServiceManager.class);
    
    private final List<ExecutorService> executorServices = new ArrayList<ExecutorService>();
    private String threadNamePattern;
    private final Map<String, ThreadPoolProfile> threadPoolProfiles = new HashMap<String, ThreadPoolProfile>();
    private ThreadPoolProfile defaultProfile;
    private CamelContext camelContext;
    private ThreadPoolFactory threadPoolFactory;

    public DefaultExecutorServiceManager(CamelContext camelContext, ThreadPoolFactory threadPoolFactory) {
        this.camelContext = camelContext;
        this.threadPoolFactory = threadPoolFactory;
        this.defaultProfile = new ThreadPoolBuilder("defaultThreadPoolProfile")
            .poolSize(10)
            .maxPoolSize(20)
            .keepAliveTime(60L, TimeUnit.SECONDS)
            .maxQueueSize(1000)
            .rejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns)
            .build();
    }

    public void registerThreadPoolProfile(ThreadPoolProfile profile) {
        threadPoolProfiles.put(profile.getId(), profile);
    }

    public ThreadPoolProfile getThreadPoolProfile(String id) {
        return threadPoolProfiles.get(id);
    }

    public ThreadPoolProfile getDefaultThreadPoolProfile() {
        return defaultProfile;
    }

    public void setDefaultThreadPoolProfile(ThreadPoolProfile defaultThreadPoolProfile) {
        defaultThreadPoolProfile.addDefaults(this.defaultProfile);
        this.defaultProfile = defaultThreadPoolProfile;
        this.defaultProfile.setDefaultProfile(true);
        LOG.info("Using custom DefaultThreadPoolProfile: " + defaultThreadPoolProfile);
    }

    public String getThreadNamePattern() {
        return threadNamePattern;
    }

    public void setThreadNamePattern(String threadNamePattern) {
        // must set camel id here in the pattern and let the other placeholders be resolved by ExecutorServiceHelper
        String name = threadNamePattern.replaceFirst("\\$\\{camelId\\}", this.camelContext.getName());
        this.threadNamePattern = name;
    }
    
    @Override
    public String resolveThreadName(String name) {
        return ThreadHelper.resolveThreadName(threadNamePattern, name);
    }

    public ExecutorService newCachedThreadPool(Object source, String name) {
        ExecutorService answer = Executors.newCachedThreadPool(new CamelThreadFactory(threadNamePattern , name, true));
        onThreadPoolCreated(answer, source, null);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new cached thread pool for source: {} with name: {}. -> {}", new Object[]{source, name, answer});
        }
        return answer;
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

    /**
     * Invoked when a new thread pool is created.
     * This implementation will invoke the {@link LifecycleStrategy#onThreadPoolAdd(org.apache.camel.CamelContext,
     * java.util.concurrent.ThreadPoolExecutor, String, String, String, String) LifecycleStrategy.onThreadPoolAdd} method,
     * which for example will enlist the thread pool in JMX management.
     *
     * @param executorService the thread pool
     * @param source          the source to use the thread pool
     * @param threadPoolProfileId profile id, if the thread pool was created from a thread pool profile
     */
    private void onThreadPoolCreated(ExecutorService executorService, Object source, String threadPoolProfileId) {
        // add to internal list of thread pools
        executorServices.add(executorService);

        String id;
        String sourceId = null;
        String routeId = null;

        // extract id from source
        if (source instanceof OptionalIdentifiedDefinition) {
            id = ((OptionalIdentifiedDefinition) source).idOrCreate(this.camelContext.getNodeIdFactory());
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
                routeId = route.idOrCreate(this.camelContext.getNodeIdFactory());
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
    
    @Override
    public ExecutorService getDefaultExecutorService(String ref, Object source) {
        ThreadPoolProfile profile = new ThreadPoolProfile(ref);
        return getExecutorService(profile, source);
    }
    
    @Override
    public ExecutorService createExecutorService(ThreadPoolProfile profile, Object source) {
        ThreadPoolProfile namedProfile = threadPoolProfiles.get(profile.getId());
        if (namedProfile != null) {
            profile.addDefaults(namedProfile);
        }
        
        profile.addDefaults(this.defaultProfile);
        
        ThreadFactory threadFactory = createThreadFactory(profile);
        ExecutorService executorService = threadPoolFactory.newThreadPool(profile, threadFactory);
        onThreadPoolCreated(executorService, source, profile.getId());
        return executorService;
    }
    
    @Override
    public ExecutorService getExecutorService(ThreadPoolProfile profile, Object source) {
        if (profile.getId() != null) {
            try {
                ExecutorService answer = camelContext.getRegistry().lookup(profile.getId(), ExecutorService.class);
                if (answer != null) {
                    LOG.debug("Looking up ExecutorService with ref: {} and found it from Registry: {}", profile.getId(), answer);
                    return answer;
                }
            } catch (NoSuchBeanException e) {
                // Jndi registry may throw this. In this case we want to continue with the profile
            }
        }
        return createExecutorService(profile, source);
    }

    private ThreadFactory createThreadFactory(ThreadPoolProfile profile) {
        // the thread name must not be null
        //ObjectHelper.notNull(profile.getThreadName(), "ThreadName");
        ThreadFactory threadFactory = new CamelThreadFactory(threadNamePattern, profile.getThreadName(), profile.isDaemon());
        return threadFactory;
    }

    @Override
    public ScheduledExecutorService getScheduledExecutorService(String ref, Object source) {
        ThreadPoolProfile profile = threadPoolProfiles.get(ref);
        if (profile == null) {
            profile = new ThreadPoolProfile(ref);
        }
        
        return getScheduledExecutorService(profile, source);
    }

    @Override
    public ScheduledExecutorService getScheduledExecutorService(ThreadPoolProfile profile, Object source) {
        if (profile.getId() != null) {
            ScheduledExecutorService answer = camelContext.getRegistry().lookup(profile.getId(), ScheduledExecutorService.class);
            if (answer != null) {
                LOG.debug("Looking up ExecutorService with ref: {} and found it from Registry: {}", profile.getId(), answer);
                return answer;
            }
        }
        
        profile.addDefaults(this.defaultProfile);
        ThreadFactory threadFactory = createThreadFactory(profile);
        ScheduledExecutorService executorService = threadPoolFactory.newScheduledThreadPool(profile, threadFactory); 
        onThreadPoolCreated(executorService, source, profile.getId());
        return executorService;
    }

    @Override
    public ExecutorService newSynchronousExecutorService(String string, Object source) {
        ExecutorService executorService = new SynchronousExecutorService();
        onThreadPoolCreated(executorService, this, "Aggregator");
        return executorService;
    }

}
