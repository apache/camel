package org.apache.camel.component.infinispan;

import java.util.Set;

import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryActivated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryInvalidated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryLoaded;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryPassivated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryVisited;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Listener(sync = true)
public class InfinispanSyncEventListener {
    private final transient Logger LOGGER = LoggerFactory.getLogger(this.getClass());
    private final InfinispanConsumer infinispanConsumer;
    private final Set<String> eventTypes;

    public InfinispanSyncEventListener(InfinispanConsumer infinispanConsumer, Set<String> eventTypes) {
        this.infinispanConsumer = infinispanConsumer;
        this.eventTypes = eventTypes;
    }

    @CacheEntryActivated
    @CacheEntryCreated
    @CacheEntryInvalidated
    @CacheEntryLoaded
    @CacheEntryModified
    @CacheEntryPassivated
    @CacheEntryRemoved
    @CacheEntryVisited
    public void processEvent(CacheEntryEvent event) {
        LOGGER.trace("Received CacheEntryEvent [{}]", event);

        if (eventTypes == null || eventTypes.isEmpty() || eventTypes.contains(event.getType().toString())) {
            infinispanConsumer.processEvent(event.getType().toString(), event.isPre(), event.getCache().getName(), event.getKey());
        }
    }
}

