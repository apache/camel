package org.apache.camel.component.infinispan;

import java.util.Set;

import org.infinispan.notifications.Listener;

@Listener(sync = false)
public class InfinispanAsyncEventListener extends InfinispanSyncEventListener {

    public InfinispanAsyncEventListener(InfinispanConsumer consumer, Set<String> eventTypes) {
        super(consumer, eventTypes);
    }
}
