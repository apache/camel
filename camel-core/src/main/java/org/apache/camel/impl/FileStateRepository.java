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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.StateRepository;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This {@link FileStateRepository} class is a file-based implementation of a {@link StateRepository}.
 */
@ManagedResource(description = "File based state repository")
public class FileStateRepository extends ServiceSupport implements StateRepository<String, String> {
    private static final Logger LOG = LoggerFactory.getLogger(FileStateRepository.class);
    private static final String STORE_DELIMITER = "\n";
    private static final String KEY_VALUE_DELIMITER = "=";
    private final AtomicBoolean init = new AtomicBoolean();
    private Map<String, String> cache;
    private File fileStore;
    private long maxFileStoreSize = 1024 * 1000L; // 1mb store file

    public FileStateRepository() {
        // default use a 1st level cache
        this.cache = new HashMap<>();
    }

    public FileStateRepository(File fileStore, Map<String, String> cache) {
        this.fileStore = fileStore;
        this.cache = cache;
    }

    /**
     * Creates a new file based repository using as 1st level cache
     *
     * @param fileStore the file store
     */
    public static FileStateRepository fileStateRepository(File fileStore) {
        return fileStateRepository(fileStore, new HashMap<>());
    }

    /**
     * Creates a new file based repository using a {@link HashMap} as 1st level cache.
     *
     * @param fileStore the file store
     * @param maxFileStoreSize the max size in bytes for the fileStore file
     */
    public static FileStateRepository fileStateRepository(File fileStore, long maxFileStoreSize) {
        FileStateRepository repository = new FileStateRepository(fileStore, new HashMap<>());
        repository.setMaxFileStoreSize(maxFileStoreSize);
        return repository;
    }

    /**
     * Creates a new file based repository using the given {@link java.util.Map} as 1st level cache.
     * <p/>
     * Care should be taken to use a suitable underlying {@link java.util.Map} to avoid this class being a
     * memory leak.
     *
     * @param store the file store
     * @param cache the cache to use as 1st level cache
     */
    public static FileStateRepository fileStateRepository(File store, Map<String, String> cache) {
        return new FileStateRepository(store, cache);
    }

    @Override
    @ManagedOperation(description = "Adds the value of the given key to the store")
    public void setState(String key, String value) {
        if (key.contains(KEY_VALUE_DELIMITER)) {
            throw new IllegalArgumentException("Key " + key + " contains illegal character: " + KEY_VALUE_DELIMITER);
        }
        if (key.contains(STORE_DELIMITER)) {
            throw new IllegalArgumentException("Key " + key + " contains illegal character: <newline>");
        }
        if (value.contains(STORE_DELIMITER)) {
            throw new IllegalArgumentException("Value " + value + " contains illegal character: <newline>");
        }
        synchronized (cache) {
            cache.put(key, value);
            if (fileStore.length() < maxFileStoreSize) {
                // just append to store
                appendToStore(key, value);
            } else {
                // trunk store and flush the cache
                trunkStore();
            }
        }
    }

    @Override
    @ManagedOperation(description = "Gets the value of the given key from store")
    public String getState(String key) {
        synchronized (cache) {
            return cache.get(key);
        }
    }

    /**
     * Resets and clears the store to force it to reload from file
     */
    @ManagedOperation(description = "Reset and reloads the file store")
    public synchronized void reset() throws IOException {
        synchronized (cache) {
            // trunk and clear, before we reload the store
            trunkStore();
            cache.clear();
            loadStore();
        }
    }

    /**
     * Appends the {@code <key,value>} pair to the file store
     *
     * @param key the state key
     */
    private void appendToStore(String key, String value) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Appending {}={} to state filestore: {}", new Object[]{key, value, fileStore});
        }
        FileOutputStream fos = null;
        try {
            // create store parent directory if missing
            File storeParentDirectory = fileStore.getParentFile();
            if (storeParentDirectory != null && !storeParentDirectory.exists()) {
                LOG.info("Parent directory of file store {} doesn't exist. Creating.", fileStore);
                if (fileStore.getParentFile().mkdirs()) {
                    LOG.info("Parent directory of file store {} successfully created.", fileStore);
                } else {
                    LOG.warn("Parent directory of file store {} cannot be created.", fileStore);
                }
            }
            // create store if missing
            if (!fileStore.exists()) {
                FileUtil.createNewFile(fileStore);
            }
            // append to store
            fos = new FileOutputStream(fileStore, true);
            fos.write(key.getBytes());
            fos.write(KEY_VALUE_DELIMITER.getBytes());
            fos.write(value.getBytes());
            fos.write(STORE_DELIMITER.getBytes());
        } catch (IOException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            IOHelper.close(fos, "Appending to file state repository", LOG);
        }
    }

    /**
     * Trunks the file store when the max store size is hit by rewriting the 1st level cache
     * to the file store.
     */
    protected void trunkStore() {
        LOG.info("Trunking state filestore: {}", fileStore);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(fileStore);
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                fos.write(entry.getKey().getBytes());
                fos.write(KEY_VALUE_DELIMITER.getBytes());
                fos.write(entry.getValue().getBytes());
                fos.write(STORE_DELIMITER.getBytes());
            }
        } catch (IOException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            IOHelper.close(fos, "Trunking file state repository", LOG);
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
            if (parent != null) {
                parent.mkdirs();
            }
            boolean created = FileUtil.createNewFile(fileStore);
            if (!created) {
                throw new IOException("Cannot create filestore: " + fileStore);
            }
        }

        LOG.trace("Loading to 1st level cache from state filestore: {}", fileStore);

        cache.clear();
        Scanner scanner = null;
        try {
            scanner = new Scanner(fileStore);
            scanner.useDelimiter(STORE_DELIMITER);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                int separatorIndex = line.indexOf(KEY_VALUE_DELIMITER);
                String key = line.substring(0, separatorIndex);
                String value = line.substring(separatorIndex + KEY_VALUE_DELIMITER.length());
                cache.put(key, value);
            }
        } catch (IOException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }

        LOG.debug("Loaded {} to the 1st level cache from state filestore: {}", cache.size(), fileStore);
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(fileStore, "fileStore", this);

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

    public Map<String, String> getCache() {
        return cache;
    }

    public void setCache(Map<String, String> cache) {
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
}
