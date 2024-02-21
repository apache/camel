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
package org.apache.camel.support.processor.idempotent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A file based implementation of {@link org.apache.camel.spi.IdempotentRepository}.
 * <p/>
 * This implementation provides a 1st-level in-memory {@link LRUCache} for fast check of the most frequently used keys.
 * When {@link #add(String)} or {@link #contains(String)} methods are being used then in case of 1st-level cache miss,
 * the underlying file is scanned which may cost additional performance. So try to find the right balance of the size of
 * the 1st-level cache, the default size is 1000. The file store has a maximum capacity of 32mb by default (you can turn
 * this off and have unlimited size). If the file store grows bigger than the maximum capacity, then the
 * {@link #getDropOldestFileStore()} (is default 1000) number of entries from the file store is dropped to reduce the
 * file store and make room for newer entries.
 */
@ManagedResource(description = "File based idempotent repository")
public class FileIdempotentRepository extends ServiceSupport implements IdempotentRepository {

    private static final Logger LOG = LoggerFactory.getLogger(FileIdempotentRepository.class);

    private static final String STORE_DELIMITER = "\n";

    private final AtomicBoolean init = new AtomicBoolean();

    private Map<String, Object> cache;
    private File fileStore;
    private long maxFileStoreSize = 32 * 1024 * 1000L; // 32mb store file
    private long dropOldestFileStore = 1000;

    public FileIdempotentRepository() {
    }

    public FileIdempotentRepository(File fileStore, Map<String, Object> set) {
        this.fileStore = fileStore;
        this.cache = set;
    }

    /**
     * Creates a new file based repository using a {@link LRUCache} as 1st level cache with a default of 1000 entries in
     * the cache.
     *
     * @param fileStore the file store
     */
    public static IdempotentRepository fileIdempotentRepository(File fileStore) {
        return fileIdempotentRepository(fileStore, 1000);
    }

    /**
     * Creates a new file based repository using a {@link LRUCache} as 1st level cache.
     *
     * @param fileStore the file store
     * @param cacheSize the cache size
     */
    public static IdempotentRepository fileIdempotentRepository(File fileStore, int cacheSize) {
        return fileIdempotentRepository(fileStore, LRUCacheFactory.newLRUCache(cacheSize));
    }

    /**
     * Creates a new file based repository using a {@link LRUCache} as 1st level cache.
     *
     * @param fileStore        the file store
     * @param cacheSize        the cache size
     * @param maxFileStoreSize the max size in bytes for the filestore file
     */
    public static IdempotentRepository fileIdempotentRepository(File fileStore, int cacheSize, long maxFileStoreSize) {
        FileIdempotentRepository repository = new FileIdempotentRepository(fileStore, LRUCacheFactory.newLRUCache(cacheSize));
        repository.setMaxFileStoreSize(maxFileStoreSize);
        return repository;
    }

    /**
     * Creates a new file based repository using the given {@link java.util.Map} as 1st level cache.
     * <p/>
     * Care should be taken to use a suitable underlying {@link java.util.Map} to avoid this class being a memory leak.
     *
     * @param store the file store
     * @param cache the cache to use as 1st level cache
     */
    public static IdempotentRepository fileIdempotentRepository(File store, Map<String, Object> cache) {
        return new FileIdempotentRepository(store, cache);
    }

    @Override
    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(String key) {
        synchronized (cache) {
            if (cache.containsKey(key)) {
                return false;
            } else {
                // always register the most used keys in the LRUCache
                cache.put(key, key);

                // now check the file store
                boolean containsInFile = containsStore(key);
                if (containsInFile) {
                    return false;
                }

                // its a new key so append to file store
                appendToStore(key);

                // check if we hit maximum capacity (if enabled) and report a warning about this
                if (maxFileStoreSize > 0 && fileStore.length() > maxFileStoreSize) {
                    LOG.warn(
                            "Maximum capacity of file store: {} hit at {} bytes. Dropping {} oldest entries from the file store",
                            fileStore, maxFileStoreSize, dropOldestFileStore);
                    trunkStore();
                }

                return true;
            }
        }
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        synchronized (cache) {
            // check 1st-level first and then fallback to check the actual file
            return cache.containsKey(key) || containsStore(key);
        }
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        boolean answer;
        synchronized (cache) {
            answer = cache.remove(key) != null;
            // remove from file cache also
            removeFromStore(key);
        }
        return answer;
    }

    @Override
    public boolean confirm(String key) {
        // noop
        return true;
    }

    @Override
    @ManagedOperation(description = "Clear the store (danger this removes all entries)")
    public void clear() {
        synchronized (cache) {
            cache.clear();
            if (cache instanceof LRUCache<String, Object> lruCache) {
                lruCache.cleanUp();
            }
            // clear file store
            clearStore();
        }
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
     * Sets the maximum file size for the file store in bytes. You can set the value to 0 or negative to turn this off,
     * and have unlimited file store size.
     * <p/>
     * The default is 32mb.
     */
    @ManagedAttribute(description = "The maximum file size for the file store in bytes")
    public void setMaxFileStoreSize(long maxFileStoreSize) {
        this.maxFileStoreSize = maxFileStoreSize;
    }

    public long getDropOldestFileStore() {
        return dropOldestFileStore;
    }

    /**
     * Sets the number of oldest entries to drop from the file store when the maximum capacity is hit to reduce disk
     * space to allow room for new entries.
     * <p/>
     * The default is 1000.
     */
    @ManagedAttribute(description = "Number of oldest elements to drop from file store if maximum file size reached")
    public void setDropOldestFileStore(long dropOldestFileStore) {
        this.dropOldestFileStore = dropOldestFileStore;
    }

    /**
     * Sets the 1st-level cache size.
     *
     * Setting cache size is only possible when using the default {@link LRUCache} cache implementation.
     */
    public void setCacheSize(int size) {
        if (cache != null && !(cache instanceof LRUCache)) {
            throw new IllegalArgumentException(
                    "Setting cache size is only possible when using the default LRUCache cache implementation");
        }
        if (cache != null) {
            cache.clear();
        }
        cache = LRUCacheFactory.newLRUCache(size);
    }

    @ManagedAttribute(description = "The current 1st-level cache size")
    public int getCacheSize() {
        if (cache != null) {
            return cache.size();
        }
        return 0;
    }

    /**
     * Reset and clears the 1st-level cache to force it to reload from file
     */
    @ManagedOperation(description = "Reset and reloads the file store")
    public synchronized void reset() throws IOException {
        synchronized (cache) {
            // run the cleanup task first
            if (cache instanceof LRUCache<String, Object> lruCache) {
                lruCache.cleanUp();
            }
            cache.clear();
            loadStore();
        }
    }

    /**
     * Checks the file store if the key exists
     *
     * @param  key the key
     * @return     <tt>true</tt> if exists in the file, <tt>false</tt> otherwise
     */
    protected boolean containsStore(final String key) {
        if (fileStore == null || !fileStore.exists()) {
            return false;
        }

        try (Scanner scanner = new Scanner(fileStore, null, STORE_DELIMITER)) {
            while (scanner.hasNext()) {
                String line = scanner.next();
                if (line.equals(key)) {
                    return true;
                }
            }
        } catch (IOException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        return false;
    }

    /**
     * Appends the given key to the file store
     *
     * @param key the key
     */
    protected void appendToStore(final String key) {
        LOG.debug("Appending: {} to idempotent filestore: {}", key, fileStore);
        FileOutputStream fos = null;
        try {
            // create store parent directory if missing
            File storeParentDirectory = fileStore.getParentFile();
            if (storeParentDirectory != null && !storeParentDirectory.exists()) {
                LOG.info("Parent directory of file store {} doesn't exist. Creating.", fileStore);
                if (fileStore.getParentFile().mkdirs()) {
                    LOG.info("Parent directory of filestore: {} successfully created.", fileStore);
                } else {
                    LOG.warn("Parent directory of filestore: {} cannot be created.", fileStore);
                }
            }
            // create store if missing
            if (!fileStore.exists()) {
                FileUtil.createNewFile(fileStore);
            }
            // append to store
            fos = new FileOutputStream(fileStore, true);
            fos.write(key.getBytes());
            fos.write(STORE_DELIMITER.getBytes());
        } catch (IOException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        } finally {
            IOHelper.close(fos, "Appending to file idempotent repository", LOG);
        }
    }

    protected synchronized void removeFromStore(String key) {
        LOG.debug("Removing: {} from idempotent filestore: {}", key, fileStore);

        // we need to re-load the entire file and remove the key and then re-write the file
        List<String> lines = new ArrayList<>();

        boolean found = false;
        try {
            try (Scanner scanner = new Scanner(fileStore, null, STORE_DELIMITER)) {
                while (scanner.hasNext()) {
                    String line = scanner.next();
                    if (key.equals(line)) {
                        found = true;
                    } else {
                        lines.add(line);
                    }
                }
            }
        } catch (IOException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        if (found) {
            // rewrite file
            LOG.debug("Rewriting idempotent filestore: {} due to key: {} removed", fileStore, key);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(fileStore);
                for (String line : lines) {
                    fos.write(line.getBytes());
                    fos.write(STORE_DELIMITER.getBytes());
                }
            } catch (IOException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            } finally {
                IOHelper.close(fos, "Rewriting file idempotent repository", LOG);
            }
        }
    }

    /**
     * Clears the file-store (danger this deletes all entries)
     */
    protected void clearStore() {
        try {
            FileUtil.deleteFile(fileStore);
            FileUtil.createNewFile(fileStore);
        } catch (IOException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    /**
     * Trunks the file store when the max store size is hit by dropping the most oldest entries.
     */
    protected synchronized void trunkStore() {
        if (fileStore == null || !fileStore.exists()) {
            return;
        }

        LOG.debug("Trunking: {} oldest entries from idempotent filestore: {}", dropOldestFileStore, fileStore);

        // we need to re-load the entire file and remove the key and then re-write the file
        List<String> lines = new ArrayList<>();

        int count = 0;
        try {
            try (Scanner scanner = new Scanner(fileStore, null, STORE_DELIMITER)) {
                while (scanner.hasNext()) {
                    String line = scanner.next();
                    count++;
                    if (count > dropOldestFileStore) {
                        lines.add(line);
                    }
                }
            }
        } catch (IOException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        if (!lines.isEmpty()) {
            // rewrite file
            LOG.debug("Rewriting idempotent filestore: {} with {} entries:", fileStore, lines.size());
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(fileStore);
                for (String line : lines) {
                    fos.write(line.getBytes());
                    fos.write(STORE_DELIMITER.getBytes());
                }
            } catch (IOException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            } finally {
                IOHelper.close(fos, "Rewriting file idempotent repository", LOG);
            }
        } else {
            // its a small file so recreate the file
            LOG.debug("Clearing idempotent filestore: {}", fileStore);
            clearStore();
        }
    }

    /**
     * Cleanup the 1st-level cache.
     */
    protected void cleanup() {
        // run the cleanup task first
        if (cache instanceof LRUCache<String, Object> lruCache) {
            lruCache.cleanUp();
        }
    }

    /**
     * Loads the given file store into the 1st level cache
     */
    protected void loadStore() throws IOException {
        // auto create starting directory if needed
        if (!fileStore.exists()) {
            LOG.debug("Creating filestore: {}", fileStore);
            File parent = fileStore.getParentFile();
            if (parent != null && !parent.exists()) {
                boolean mkdirsResult = parent.mkdirs();
                if (!mkdirsResult) {
                    LOG.warn("Cannot create the filestore directory at: {}", parent);
                }
            }
            boolean created = FileUtil.createNewFile(fileStore);
            if (!created) {
                throw new IOException("Cannot create filestore: " + fileStore);
            }
        }

        LOG.trace("Loading to 1st level cache from idempotent filestore: {}", fileStore);

        cache.clear();
        try (Scanner scanner = new Scanner(fileStore, null, STORE_DELIMITER)) {
            while (scanner.hasNext()) {
                String line = scanner.next();
                cache.put(line, line);
            }
        } catch (IOException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        LOG.debug("Loaded {} to the 1st level cache from idempotent filestore: {}", cache.size(), fileStore);
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(fileStore, "fileStore", this);

        if (this.cache == null) {
            // default use a 1st level cache
            this.cache = LRUCacheFactory.newLRUCache(1000);
        }

        // init store if not loaded before
        if (init.compareAndSet(false, true)) {
            loadStore();
        }
    }

    @Override
    protected void doStop() throws Exception {
        // run the cleanup task first
        if (cache instanceof LRUCache<String, Object> lruCache) {
            lruCache.cleanUp();
        }

        cache.clear();
        init.set(false);
    }

}
