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
package org.apache.camel.processor.idempotent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * A file based implementation of {@link org.apache.camel.spi.IdempotentRepository}.
 * <p/>
 * Care should be taken to use a suitable underlying {@link java.util.Map} to avoid this class being a
 * memory leak.
 *
 * @version 
 */
@ManagedResource("FileIdempotentRepository")
public class FileIdempotentRepository extends ServiceSupport implements IdempotentRepository<String> {
    private static final transient Logger LOG = LoggerFactory.getLogger(FileIdempotentRepository.class);
    private static final String STORE_DELIMITER = "\n";
    private Map<String, Object> cache;
    private File fileStore;
    private long maxFileStoreSize = 1024 * 1000L; // 1mb store file
    private AtomicBoolean init = new AtomicBoolean();

    public FileIdempotentRepository() {
        // default use a 1st level cache 
        this.cache = new LRUCache<String, Object>(1000);
    }

    public FileIdempotentRepository(File fileStore, Map<String, Object> set) {
        this.fileStore = fileStore;
        this.cache = set;
    }

    /**
     * Creates a new file based repository using a {@link org.apache.camel.util.LRUCache}
     * as 1st level cache with a default of 1000 entries in the cache.
     *
     * @param fileStore  the file store
     */
    public static IdempotentRepository<String> fileIdempotentRepository(File fileStore) {
        return fileIdempotentRepository(fileStore, 1000);
    }

    /**
     * Creates a new file based repository using a {@link org.apache.camel.util.LRUCache}
     * as 1st level cache.
     *
     * @param fileStore  the file store
     * @param cacheSize  the cache size
     */
    public static IdempotentRepository<String> fileIdempotentRepository(File fileStore, int cacheSize) {
        return fileIdempotentRepository(fileStore, new LRUCache<String, Object>(cacheSize));
    }

    /**
     * Creates a new file based repository using a {@link org.apache.camel.util.LRUCache}
     * as 1st level cache.
     *
     * @param fileStore  the file store
     * @param cacheSize  the cache size
     * @param maxFileStoreSize  the max size in bytes for the filestore file 
     */
    public static IdempotentRepository<String> fileIdempotentRepository(File fileStore, int cacheSize, long maxFileStoreSize) {
        FileIdempotentRepository repository = new FileIdempotentRepository(fileStore, new LRUCache<String, Object>(cacheSize));
        repository.setMaxFileStoreSize(maxFileStoreSize);
        return repository;
    }

    /**
     * Creates a new file based repository using the given {@link java.util.Map}
     * as 1st level cache.
     * <p/>
     * Care should be taken to use a suitable underlying {@link java.util.Map} to avoid this class being a
     * memory leak.
     *
     * @param store  the file store
     * @param cache  the cache to use as 1st level cache
     */
    public static IdempotentRepository<String> fileIdempotentRepository(File store, Map<String, Object> cache) {
        return new FileIdempotentRepository(store, cache);
    }

    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(String key) {
        synchronized (cache) {
            if (cache.containsKey(key)) {
                return false;
            } else {
                cache.put(key, key);
                if (fileStore.length() < maxFileStoreSize) {
                    // just append to store
                    appendToStore(key);
                } else {
                    // trunk store and flush the cache
                    trunkStore();
                }

                return true;
            }
        }
    }

    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        synchronized (cache) {
            return cache.containsKey(key);
        }
    }

    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        boolean answer;
        synchronized (cache) {
            answer = cache.remove(key) != null;
            // trunk store and flush the cache on remove
            trunkStore();
        }
        return answer;
    }

    public boolean confirm(String key) {
        // noop
        return true;
    }

    public File getFileStore() {
        return fileStore;
    }

    public void setFileStore(File fileStore) {
        this.fileStore = fileStore;
    }

    @ManagedAttribute(description = "The file path for the store")
    public String getFilePath() {
        return fileStore.getPath();
    }

    public Map<String, Object> getCache() {
        return cache;
    }

    public void setCache(Map<String, Object> cache) {
        this.cache = cache;
    }

    @ManagedAttribute(description = "The maximum file size for the file store in bytes")
    public long getMaxFileStoreSize() {
        return maxFileStoreSize;
    }

    /**
     * Sets the maximum file size for the file store in bytes.
     * <p/>
     * The default is 1mb.
     */
    @ManagedAttribute(description = "The maximum file size for the file store in bytes")
    public void setMaxFileStoreSize(long maxFileStoreSize) {
        this.maxFileStoreSize = maxFileStoreSize;
    }

    /**
     * Sets the cache size
     */
    public void setCacheSize(int size) {
        if (cache != null) {
            cache.clear();
        }
        cache = new LRUCache<String, Object>(size);
    }

    @ManagedAttribute(description = "The current cache size")
    public int getCacheSize() {
        if (cache != null) {
            return cache.size();
        }
        return 0;
    }

    /**
     * Reset and clears the store to force it to reload from file
     */
    @ManagedOperation(description = "Reset and reloads the file store")
    public synchronized void reset() {
        synchronized (cache) {
            // trunk and clear, before we reload the store
            trunkStore();
            cache.clear();
            loadStore();
        }
    }

    /**
     * Appends the given message id to the file store
     *
     * @param messageId  the message id
     */
    protected void appendToStore(final String messageId) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Appending " + messageId + " to idempotent filestore: " + fileStore);
        }
        FileOutputStream fos = null;
        try {
            // create store if missing
            if (!fileStore.exists()) {
                fileStore.createNewFile();
            }
            // append to store
            fos = new FileOutputStream(fileStore, true);
            fos.write(messageId.getBytes());
            fos.write(STORE_DELIMITER.getBytes());
        } catch (IOException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            IOHelper.close(fos, "Appending to file idempotent repository", LOG);
        }
    }

    /**
     * Trunks the file store when the max store size is hit by rewriting the 1st level cache
     * to the file store.
     */
    protected void trunkStore() {
        LOG.info("Trunking idempotent filestore: {}", fileStore);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(fileStore);
            for (String key : cache.keySet()) {
                fos.write(key.getBytes());
                fos.write(STORE_DELIMITER.getBytes());
            }
        } catch (IOException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            IOHelper.close(fos, "Trunking file idempotent repository", LOG);
        }
    }

    /**
     * Loads the given file store into the 1st level cache
     */
    protected void loadStore() {
        LOG.trace("Loading to 1st level cache from idempotent filestore: {}", fileStore);

        if (!fileStore.exists()) {
            return;
        }

        cache.clear();
        Scanner scanner = null;
        try {
            scanner = new Scanner(fileStore);
            scanner.useDelimiter(STORE_DELIMITER);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                cache.put(line, line);
            }
        } catch (IOException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Loaded " + cache.size() + " to the 1st level cache from idempotent filestore: " + fileStore);
        }
    }

    @Override
    protected void doStart() throws Exception {
        // init store if not loaded before
        if (init.compareAndSet(false, true)) {
            loadStore();
        }
    }

    @Override
    protected void doStop() throws Exception {
        // reset will trunk and clear the cache
        trunkStore();
        cache.clear();
        init.set(false);
    }

}
