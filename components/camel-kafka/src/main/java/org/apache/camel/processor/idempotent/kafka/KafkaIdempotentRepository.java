package org.apache.camel.processor.idempotent.kafka;

import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.processor.idempotent.FileIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.LRUCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * A Kafka topic based implementation of {@link org.apache.camel.spi.IdempotentRepository}.
 *
 *
 * @author jkorab
 */
@ManagedResource(description = "Kafka IdempotentRepository")
public class KafkaIdempotentRepository extends ServiceSupport implements IdempotentRepository<String> {

    private static final Logger LOG = LoggerFactory.getLogger(FileIdempotentRepository.class);
    private Map<String, Object> cache;

    public KafkaIdempotentRepository() {
        // default use a 1st level cache
        this.cache = new LRUCache<String, Object>(1000);
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {

    }

    @Override
    protected void doStart() throws Exception {
        // per set up the consumer
    }

    @Override
    protected void doStop() throws Exception {
        // empty
    }

    @Override
    public boolean add(String key) {
        synchronized (cache) {
            if (cache.containsKey(key)) {
                return false;
            } else {
                cache.put(key, key);
                broadcastInsert(key);

                return true;
            }
        }
    }

    private void broadcastInsert(String key) {
        // TODO implement
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        synchronized (cache) {
            return cache.containsKey(key);
        }
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        return false;
    }

    @Override
    public boolean confirm(String key) {
        return false;
    }

    @Override
    public void clear() {
        // TODO mark all keys on the topic as having been consumed
    }

}
