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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Metadata;
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
 * <p>
 * This implementation provides a 1st-level in-memory {@link LRUCache} for fast check of the most frequently used keys.
 * When {@link #add(String)} or {@link #contains(String)} methods are being used then in case of 1st-level cache miss,
 * the underlying file is scanned which may cost additional performance. So try to find the right balance of the size of
 * the 1st-level cache, the default size is 1000. The file store has a maximum capacity of 32mb by default (you can turn
 * this off and have unlimited size). If the file store grows bigger than the maximum capacity, then the
 * {@link #getDropOldestFileStore()} (is default 1000) number of entries from the file store is dropped to reduce the
 * file store and make room for newer entries.
 */
@Metadata(label = "bean",
          description = "A file based idempotent repository. Comes with 1st-level in-memory cache for fast check of the most frequently used keys.",
          annotations = { "interfaceName=org.apache.camel.spi.IdempotentRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "File based idempotent repository")
public class FileIdempotentRepository extends ServiceSupport implements IdempotentRepository {

    private static final Logger LOG = LoggerFactory.getLogger(FileIdempotentRepository.class);
    private static final String STORE_DELIMITER = "\n";

    private final AtomicBoolean init = new AtomicBoolean();
    private Map<String, Object> cache;
    private final Lock lock = new ReentrantLock();
    private final Lock cacheAndStoreLock = new ReentrantLock();

    @Metadata(description = "The maximum size of the 1st-level in-memory cache", defaultValue = "1000")
    private int cacheSize;
    @Metadata(description = "File name of the repository (incl directory). Either fileName or fileStore must be set.")
    private String fileName;
    @Metadata(description = "File of the repository (incl directory) as a java.io.File. Either fileName or fileStore must be"
                            + " set.")
    private File fileStore;
    @Metadata(description = "The maximum file size for the file store in bytes. The default value is 32mb",
              defaultValue = "" + 32 * 1024 * 1000L)
    private long maxFileStoreSize = 32 * 1024 * 1000L; // 32mb store file
    @Metadata(description = "Sets the number of oldest entries to drop from the file store when the maximum capacity is hit to reduce disk"
                            + " space to allow room for new entries.",
              defaultValue = "1000")
    private long dropOldestFileStore = 1000;
    private boolean rewriteInPlaceLogged;

    public FileIdempotentRepository() {
    }

    public FileIdempotentRepository(File fileStore, Map<String, Object> cache) {
        this.fileStore = fileStore;
        this.fileName = fileStore != null ? fileStore.getPath() : null;
        this.cache = cache;
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
        if (cacheSize <= 0) {
            throw new IllegalArgumentException("cacheSize must be greater than 0, was: " + cacheSize);
        }
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
        if (cacheSize <= 0) {
            throw new IllegalArgumentException("cacheSize must be greater than 0, was: " + cacheSize);
        }
        FileIdempotentRepository repository = new FileIdempotentRepository(fileStore, LRUCacheFactory.newLRUCache(cacheSize));
        repository.setMaxFileStoreSize(maxFileStoreSize);
        return repository;
    }

    /**
     * Creates a new file based repository using the given {@link java.util.Map} as 1st level cache.
     * <p>
     * Care should be taken to use a suitable underlying {@link java.util.Map} to avoid this class being a memory leak.
     *
     * @param store the file store
     * @param cache the cache to use as 1st level cache
     */
    public static IdempotentRepository fileIdempotentRepository(File store, Map<String, Object> cache) {
        return new FileIdempotentRepository(store, cache);
    }

    /**
     * Creates a new file based repository using a {@link LRUCache} as 1st level cache with a default of 1000 entries in
     * the cache.
     *
     * @param fileName the file name of the file store (incl directory)
     */
    public static IdempotentRepository fileIdempotentRepository(String fileName) {
        return fileIdempotentRepository(new File(fileName));
    }

    /**
     * Creates a new file based repository using a {@link LRUCache} as 1st level cache.
     *
     * @param fileName  the file name of the file store (incl directory)
     * @param cacheSize the cache size
     */
    public static IdempotentRepository fileIdempotentRepository(String fileName, int cacheSize) {
        return fileIdempotentRepository(new File(fileName), cacheSize);
    }

    /**
     * Creates a new file based repository using a {@link LRUCache} as 1st level cache.
     *
     * @param fileName         the file name of the file store (incl directory)
     * @param cacheSize        the cache size
     * @param maxFileStoreSize the max size in bytes for the filestore file
     */
    public static IdempotentRepository fileIdempotentRepository(String fileName, int cacheSize, long maxFileStoreSize) {
        return fileIdempotentRepository(new File(fileName), cacheSize, maxFileStoreSize);
    }

    /**
     * Creates a new file based repository using the given {@link java.util.Map} as 1st level cache.
     * <p>
     * Care should be taken to use a suitable underlying {@link java.util.Map} to avoid this class being a memory leak.
     *
     * @param fileName the file name of the file store (incl directory)
     * @param cache    the cache to use as 1st level cache
     */
    public static IdempotentRepository fileIdempotentRepository(String fileName, Map<String, Object> cache) {
        return fileIdempotentRepository(new File(fileName), cache);
    }

    @Override
    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(String key) {
        cacheAndStoreLock.lock();
        try {
            if (cache.containsKey(key)) {
                return false;
            } else {
                // now check the file store
                boolean containsInFile = containsStore(key);
                if (containsInFile) {
                    // always register the most used keys in the LRUCache
                    cache.put(key, key);
                    return false;
                }

                // its a new key so append to file store, and only then register it in the LRUCache: a key that
                // could not be stored is not added, so that adding it again is not taken for a duplicate
                appendToStore(key);
                cache.put(key, key);

                // check if we hit maximum capacity (if enabled) and report a warning about this
                if (maxFileStoreSize > 0 && fileStore.length() > maxFileStoreSize) {
                    LOG.warn(
                            "Maximum capacity of file store: {} hit at {} bytes. Dropping {} oldest entries from the file store",
                            fileStore, maxFileStoreSize, dropOldestFileStore);
                    try {
                        trunkStore();
                    } catch (RuntimeException e) {
                        // the key is stored, so the add succeeded (failing it would take the retry of the message
                        // for a duplicate): the oldest entries are dropped on the next add instead
                        LOG.warn(
                                "Cannot drop the oldest entries from the file store: {} due to: {}. Trying again on the next add.",
                                fileStore, e.getMessage(), e);
                    }
                }

                return true;
            }
        } finally {
            cacheAndStoreLock.unlock();
        }
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        cacheAndStoreLock.lock();
        try {
            // check 1st-level first and then fallback to check the actual file
            return cache.containsKey(key) || containsStore(key);
        } finally {
            cacheAndStoreLock.unlock();
        }
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        boolean answer;
        cacheAndStoreLock.lock();
        try {
            answer = cache.remove(key) != null;
            // remove from file cache also
            removeFromStore(key);
        } finally {
            cacheAndStoreLock.unlock();
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
        cacheAndStoreLock.lock();
        try {
            cache.clear();
            if (cache instanceof LRUCache<String, Object> lruCache) {
                lruCache.cleanUp();
            }
            // clear file store
            clearStore();
        } finally {
            cacheAndStoreLock.unlock();
        }
    }

    public File getFileStore() {
        return fileStore;
    }

    /**
     * Sets the file of the repository (incl directory). This also sets the file name to the path of the file.
     *
     * @param fileStore the file store, or {@code null} to clear the file store and the file name
     */
    public void setFileStore(File fileStore) {
        this.fileStore = fileStore;
        this.fileName = fileStore != null ? fileStore.getPath() : null;
    }

    /**
     * Gets the file name of the repository (incl directory).
     *
     * @return the file name, or {@code null} if no file store or file name is set
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the file name of the repository (incl directory), such as when the repository is declared as a bean in YAML
     * or properties. This also sets the file store to this file.
     *
     * @param fileName the file name, or {@code null} to clear the file name and the file store
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
        this.fileStore = fileName != null ? new File(fileName) : null;
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
     * <p>
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
     * <p>
     * The default is 1000.
     */
    @ManagedAttribute(description = "Number of oldest elements to drop from file store if maximum file size reached")
    public void setDropOldestFileStore(long dropOldestFileStore) {
        this.dropOldestFileStore = dropOldestFileStore;
    }

    @ManagedAttribute(description = "The current 1st-level cache size (elements in cache)")
    public int getCacheSize() {
        if (cache != null) {
            return cache.size();
        }
        return 0;
    }

    /**
     * Sets the 1st-level maximum cache size.
     */
    public void setCacheSize(int size) {
        this.cacheSize = size;
    }

    @ManagedAttribute(description = "The 1st-level maximum cache size")
    public int getMaxCacheSize() {
        return cacheSize;
    }

    /**
     * Reset and clears the 1st-level cache to force it to reload from file
     */
    @ManagedOperation(description = "Reset and reloads the file store")
    public void reset() throws IOException {
        lock.lock();
        try {
            cacheAndStoreLock.lock();
            try {
                // run the cleanup task first
                if (cache instanceof LRUCache<String, Object> lruCache) {
                    lruCache.cleanUp();
                }
                cache.clear();
                loadStore();
            } finally {
                cacheAndStoreLock.unlock();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks the file store if the key exists
     *
     * @param  key the key
     * @return     <code>true</code> if exists in the file, <code>false</code> otherwise
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
            // append to store (as a single write, so the key is not split from its delimiter)
            fos = new FileOutputStream(fileStore, true);
            fos.write((key + STORE_DELIMITER).getBytes());
        } catch (IOException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        } finally {
            IOHelper.close(fos, "Appending to file idempotent repository", LOG);
        }
    }

    protected void removeFromStore(String key) {
        lock.lock();
        try {
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
                rewriteStore(lines);
            }
        } finally {
            lock.unlock();
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
    protected void trunkStore() {
        lock.lock();
        try {
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
                rewriteStore(lines);
            } else {
                // its a small file so recreate the file
                LOG.debug("Clearing idempotent filestore: {}", fileStore);
                clearStore();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Rewrites the file store with the given keys. The keys are written to a temporary file which then replaces the
     * file store, so the file store is never left truncated or half written (such as when writing fails, or the JVM
     * stops while writing). When the temporary file cannot be created or cannot replace the file store (such as a file
     * store that is a single file mounted into a container), the file store is rewritten in place instead.
     */
    private void rewriteStore(List<String> keys) {
        Path target = fileStore.toPath();
        Path tmp;
        try {
            if (Files.exists(target)) {
                // replace the real file of a symlinked store (so the link is kept, and the temporary file is on the
                // same file system as the store)
                target = target.toRealPath();
            }
            tmp = createTemporaryStore(target);
        } catch (IOException e) {
            rewriteStoreInPlace(target, keys, "a temporary file cannot be created next to it", e);
            return;
        }

        boolean written = false;
        try {
            writeStore(tmp, keys);
            written = true;
        } finally {
            if (!written) {
                // the file store is unchanged
                FileUtil.deleteFile(tmp.toFile());
            }
        }

        try {
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // a failed move leaves the file store unchanged
            FileUtil.deleteFile(tmp.toFile());
            rewriteStoreInPlace(target, keys, "a temporary file cannot replace it", e);
        }
    }

    /**
     * Creates the temporary file next to the file store, with the permissions of the file store (before the keys are
     * written to it, so the keys are never readable with wider permissions than the file store)
     */
    private static Path createTemporaryStore(Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.deleteIfExists(tmp);
        boolean created = false;
        try {
            if (Files.exists(target) && Files.getFileAttributeView(target, PosixFileAttributeView.class) != null) {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(target);
                Files.createFile(tmp, PosixFilePermissions.asFileAttribute(permissions));
                created = true;
                // the umask may have removed some of them
                Files.setPosixFilePermissions(tmp, permissions);
            } else {
                Files.createFile(tmp);
                created = true;
            }
            return tmp;
        } catch (IOException e) {
            if (created) {
                FileUtil.deleteFile(tmp.toFile());
            }
            throw e;
        }
    }

    private void rewriteStoreInPlace(Path target, List<String> keys, String reason, IOException cause) {
        if (!rewriteInPlaceLogged) {
            rewriteInPlaceLogged = true;
            LOG.warn("Rewriting idempotent filestore: {} in place, as {} due to: {}. A JVM stop or an I/O error while"
                     + " rewriting can lose entries of the filestore.",
                    fileStore, reason, cause.getMessage(), cause);
        } else {
            LOG.debug("Rewriting idempotent filestore: {} in place, as {} due to: {}", fileStore, reason,
                    cause.getMessage());
        }
        writeStore(target, keys);
    }

    private void writeStore(Path file, List<String> keys) {
        try (FileOutputStream fos = new FileOutputStream(file.toFile());
             OutputStream out = new BufferedOutputStream(fos)) {
            writeKeys(out, keys);
            out.flush();
            fos.getFD().sync();
        } catch (IOException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    /**
     * Writes the keys, each followed by the delimiter, when the file store is rewritten
     */
    void writeKeys(OutputStream out, List<String> keys) throws IOException {
        for (String key : keys) {
            out.write((key + STORE_DELIMITER).getBytes());
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
        ObjectHelper.notNull(fileStore, "fileStore or fileName", this);

        if (this.cache == null) {
            // default use a 1st level cache
            this.cache = LRUCacheFactory.newLRUCache(cacheSize <= 0 ? 1000 : cacheSize);
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
