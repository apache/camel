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

import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A file based implementation of {@link org.apache.camel.spi.IdempotentRepository}.
 * <p/>
 * Care should be taken to use a suitable underlying {@link java.util.Map} to avoid this class being a
 * memory leak.
 *
 * @version $Revision$
 */
public class FileIdempotentRepository implements IdempotentRepository<String> {
    private static final transient Log LOG = LogFactory.getLog(FileIdempotentRepository.class);
    private static final String STORE_DELIMITER = "\n";
    private Map<String, Object> cache;
    private File store;
    private long maxStoreSize = 1024 * 1000L; // 1mb store file

    public FileIdempotentRepository(final File store, final Map<String, Object> set) {
        this.store = store;
        this.cache = set;
        loadStore();
    }

    /**
     * Creates a new file based repository using a {@link org.apache.camel.util.LRUCache}
     * as 1st level cache with a default of 1000 entries in the cache.
     *
     * @param store  the file store
     */
    public static IdempotentRepository fileIdempotentRepository(File store) {
        return fileIdempotentRepository(store, 1000);
    }

    /**
     * Creates a new file based repository using a {@link org.apache.camel.util.LRUCache}
     * as 1st level cache.
     *
     * @param store  the file store
     * @param cacheSize  the cache size
     */
    public static IdempotentRepository fileIdempotentRepository(File store, int cacheSize) {
        return fileIdempotentRepository(store, new LRUCache<String, Object>(cacheSize));
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
    public static IdempotentRepository fileIdempotentRepository(File store, Map<String, Object> cache) {
        return new FileIdempotentRepository(store, cache);
    }

    public boolean add(String messageId) {
        synchronized (cache) {
            if (cache.containsKey(messageId)) {
                return false;
            } else {
                cache.put(messageId, messageId);
                if (store.length() < maxStoreSize) {
                    // just append to store
                    appendToStore(messageId);
                } else {
                    // trunk store and flush the cache
                    trunkStore();
                }

                return true;
            }
        }
    }

    public boolean contains(String key) {
        synchronized (cache) {
            return cache.containsKey(key);
        }
    }

    public File getStore() {
        return store;
    }

    public void setStore(File store) {
        this.store = store;
    }

    public Map<String, Object> getCache() {
        return cache;
    }

    public void setCache(Map<String, Object> cache) {
        this.cache = cache;
    }

    public long getMaxStoreSize() {
        return maxStoreSize;
    }

    /**
     * Sets the maximum filesize for the file store in bytes.
     * <p/>
     * The default is 1mb.
     */
    public void setMaxStoreSize(long maxStoreSize) {
        this.maxStoreSize = maxStoreSize;
    }

    /**
     * Appends the given message id to the file store
     *
     * @param messageId  the message id
     */
    protected void appendToStore(final String messageId) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Appending " + messageId + " to idempotent filestore: " + store);
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(store, true);
            fos.write(messageId.getBytes());
            fos.write(STORE_DELIMITER.getBytes());
        } catch (IOException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            ObjectHelper.close(fos, "Appending to file idempotent repository", LOG);
        }
    }

    /**
     * Trunks the file store when the max store size is hit by rewriting the 1st level cache
     * to the file store.
     */
    protected void trunkStore() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Trunking idempotent filestore: " + store);
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(store);
            for (String key : cache.keySet()) {
                fos.write(key.getBytes());
                fos.write(STORE_DELIMITER.getBytes());
            }
        } catch (IOException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            ObjectHelper.close(fos, "Trunking file idempotent repository", LOG);
        }
    }

    /**
     * Loads the given file store into the 1st level cache
     */
    protected void loadStore() {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Loading to 1st level cache from idempotent filestore: " + store);
        }

        if (!store.exists()) {
            return;
        }

        cache.clear();
        Scanner scanner = null;
        try {
            scanner = new Scanner(store);
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
            LOG.debug("Loaded " + cache.size() + " to the 1st level cache from idempotent filestore: " + store);
        }
    }

}