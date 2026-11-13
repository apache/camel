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
package org.apache.camel.component.kubernetes;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.dsl.Informable;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.BasicItemStore;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.kubernetes.KubernetesHelper.WATCH_AGAIN_DELAY_MILLIS;
import static org.apache.camel.component.kubernetes.KubernetesHelper.WATCH_AGAIN_MAX_DELAY_MILLIS;

/**
 * Watches Kubernetes resources with an informer, until a service is stopped, and calls back when a resource whose name
 * matches is updated. Used by the tasks that reload the CamelContext when a secret or a config map is updated.
 * <p>
 * The informer lists the resources, and then watches them. When the Kubernetes client closes the watch, for example
 * with 410 Gone because the resource version of the watch is too old (which happens to long-running watches), the
 * informer lists the resources again and compares them with the ones it has, so an update made while the resources were
 * not watched is not missed. Once started, the informer retries a failed list or watch by itself.
 * <p>
 * If the informer cannot be started (the API server cannot be reached, answers 403 because the RBAC rules of the
 * service account are not there yet, ...), or stops for good (the Kubernetes client stops it after some watch errors),
 * a new one is started after {@link KubernetesHelper#WATCH_AGAIN_DELAY_MILLIS}, with a delay that doubles up to
 * {@link KubernetesHelper#WATCH_AGAIN_MAX_DELAY_MILLIS} while it cannot be started. The new informer starts from the
 * resources the previous one had, so an update made in between is not missed either.
 * <p>
 * The informer keeps only the resources whose name matches, not all the resources of the namespace.
 *
 * @param <T> the type of the resources
 */
public final class KubernetesInformerLoop<T extends HasMetadata> {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesInformerLoop.class);

    private final ServiceSupport service;
    private final String kind;
    private final Informable<T> resources;
    private final Predicate<String> nameFilter;
    private final Consumer<T> onUpdate;
    // the resources whose name matches, kept from one informer to the next
    private final MatchingItemStore store = new MatchingItemStore();
    private volatile SharedIndexInformer<T> current;
    private volatile boolean stopped;
    // released when the informer stops for good, when the wait before the next informer is over, and when the service
    // is stopped
    private volatile CountDownLatch released;

    /**
     * @param service    the service that runs this loop, which ends when the service is stopped
     * @param kind       the kind of the resources, for logging (Secrets, ConfigMaps)
     * @param resources  the resources to watch
     * @param nameFilter whether a resource with this name is watched
     * @param onUpdate   called when a watched resource is updated
     */
    public KubernetesInformerLoop(ServiceSupport service, String kind, Informable<T> resources, Predicate<String> nameFilter,
                                  Consumer<T> onUpdate) {
        this.service = service;
        this.kind = kind;
        this.resources = resources;
        this.nameFilter = nameFilter;
        this.onUpdate = onUpdate;
    }

    /**
     * Watches the resources until the service, or this loop, is stopped. The delay before starting a new informer is
     * {@link KubernetesHelper#WATCH_AGAIN_DELAY_MILLIS} after the informer stopped or could not be started the first
     * time, and doubles while it cannot be started.
     */
    public void run() {
        long delay = WATCH_AGAIN_DELAY_MILLIS;
        while (isRunAllowed()) {
            final CountDownLatch isInformerStopped = new CountDownLatch(1);
            released = isInformerStopped;
            SharedIndexInformer<T> informer = null;
            try {
                informer = resources.runnableInformer(0);
                informer.removeNamespaceIndex().itemStore(store).addEventHandler(new UpdateHandler());
                current = informer;
                // a stop before the informer was published could not stop it
                if (!isRunAllowed()) {
                    return;
                }
                // returns once the resources are listed and watched, and fails if they cannot be
                informer.run();
                delay = WATCH_AGAIN_DELAY_MILLIS;
                AtomicReference<Throwable> cause = new AtomicReference<>();
                informer.stopped().whenComplete((r, e) -> {
                    cause.set(e);
                    isInformerStopped.countDown();
                });
                // Wait till the informer stops. A stop before the latch was published could not release it
                if (isRunAllowed()) {
                    isInformerStopped.await();
                }
                if (isRunAllowed()) {
                    Throwable e = cause.get();
                    LOG.warn("The informer of Kubernetes {} stopped, watching again in {} ms: {}", kind,
                            WATCH_AGAIN_DELAY_MILLIS, e != null ? e.getMessage() : null);
                }
            } catch (InterruptedException e) {
                LOG.debug("Interrupted while watching Kubernetes {}: {}", kind, e.getMessage());
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (!isRunAllowed()) {
                    return;
                }
                LOG.warn("Cannot watch Kubernetes {}, trying again in {} ms: {}", kind, delay, e.getMessage());
                LOG.debug("Cannot watch Kubernetes {}", kind, e);
            } finally {
                current = null;
                if (informer != null) {
                    informer.stop();
                }
            }
            if (!waitBeforeWatchingAgain(delay)) {
                return;
            }
            delay = Math.min(delay * 2, WATCH_AGAIN_MAX_DELAY_MILLIS);
        }
    }

    /**
     * Stops the informer, and ends the wait before the next one, so the thread that runs this loop ends. Called when
     * the service is stopped.
     */
    public void stop() {
        stopped = true;
        SharedIndexInformer<T> informer = current;
        if (informer != null) {
            informer.stop();
        }
        CountDownLatch latch = released;
        if (latch != null) {
            latch.countDown();
        }
    }

    private boolean isRunAllowed() {
        return !stopped && service.isRunAllowed();
    }

    /**
     * @return false if the service was stopped, or the thread interrupted, while waiting
     */
    private boolean waitBeforeWatchingAgain(long delay) {
        CountDownLatch pause = new CountDownLatch(1);
        released = pause;
        try {
            if (isRunAllowed()) {
                pause.await(delay, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return isRunAllowed();
    }

    /**
     * Keeps only the resources whose name matches, so the informer does not keep all the resources of the namespace.
     */
    private final class MatchingItemStore extends BasicItemStore<T> {

        MatchingItemStore() {
            super(Cache::metaNamespaceKeyFunc);
        }

        @Override
        public T put(String key, T obj) {
            if (nameFilter.test(obj.getMetadata().getName())) {
                return super.put(key, obj);
            }
            return null;
        }
    }

    private final class UpdateHandler implements ResourceEventHandler<T> {

        @Override
        public void onAdd(T obj) {
            // the resources that exist when the informer starts, and the new ones, are not updates
        }

        @Override
        public void onUpdate(T oldObj, T newObj) {
            // the informer tells about a resource it lists again only when its resource version changed
            onUpdate.accept(newObj);
        }

        @Override
        public void onDelete(T obj, boolean deletedFinalStateUnknown) {
            // a deleted resource is not an update
        }
    }
}
