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
package org.apache.camel.component.leveldb;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.Service;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBFactory;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages access to a shared <a href="https://github.com/fusesource/leveldbjni/">LevelDB</a> file.
 * <p/>
 * Will by default not sync writes which allows it to be faster.
 * You can force syncing by setting the sync option to <tt>true</tt>.
 */
public class LevelDBFile implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(LevelDBFile.class);

    private DB db;
    private File file;
    private int writeBufferSize = 4 << 20;
    private int maxOpenFiles = 1000;
    private int blockRestartInterval = 16;
    private int blockSize = 4 * 1024;
    private String compressionType;
    private boolean verifyChecksums = true;
    private boolean paranoidChecks;
    private long cacheSize = 32 << 20;
    private boolean sync;

    public DB getDb() {
        return db;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) throws IOException {
        this.file = file;
    }

    public String getFileName() throws IOException {
        return file.getCanonicalPath();
    }

    public void setFileName(String fileName) {
        this.file = new File(fileName);
    }

    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    public void setWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
    }

    public int getMaxOpenFiles() {
        return maxOpenFiles;
    }

    public void setMaxOpenFiles(int maxOpenFiles) {
        this.maxOpenFiles = maxOpenFiles;
    }

    public int getBlockRestartInterval() {
        return blockRestartInterval;
    }

    public void setBlockRestartInterval(int blockRestartInterval) {
        this.blockRestartInterval = blockRestartInterval;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    public String getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(String compressionType) {
        this.compressionType = compressionType;
    }

    public boolean isVerifyChecksums() {
        return verifyChecksums;
    }

    public void setVerifyChecksums(boolean verifyChecksums) {
        this.verifyChecksums = verifyChecksums;
    }

    public boolean isParanoidChecks() {
        return paranoidChecks;
    }

    public void setParanoidChecks(boolean paranoidChecks) {
        this.paranoidChecks = paranoidChecks;
    }

    public long getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(long cacheSize) {
        this.cacheSize = cacheSize;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public WriteOptions getWriteOptions() {
        WriteOptions options = new WriteOptions();
        options.sync(sync);
        return options;
    }

    @Override
    public void start() {
        if (getFile() == null) {
            throw new IllegalArgumentException("A file must be configured");
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting LevelDB using file: {}", getFile());
        }

        Options options = new Options().writeBufferSize(writeBufferSize).maxOpenFiles(maxOpenFiles)
                .blockRestartInterval(blockRestartInterval).blockSize(blockSize).verifyChecksums(verifyChecksums)
                .paranoidChecks(paranoidChecks).cacheSize(cacheSize);

        if ("snappy".equals(compressionType)) {
            options.compressionType(CompressionType.SNAPPY);
        } else {
            options.compressionType(CompressionType.NONE);
        }

        options.createIfMissing(true);
        try {
            final Path dbFile = Paths.get(this.getFileName());
            Files.createDirectories(dbFile.getParent());
            DBFactory factory = getFactory();
            db = factory.open(getFile(), options);
        } catch (IOException ioe) {
            throw new RuntimeException("Error opening LevelDB with file " + getFile(), ioe);
        }
    }

    private DBFactory getFactory() {
        String[] classNames = new String[] {
            "org.fusesource.leveldbjni.JniDBFactory",
            "org.iq80.leveldb.impl.Iq80DBFactory"
        };
        for (String cn : classNames) {
            try {
                Class<?> clz = ObjectHelper.loadClass(cn, getClass().getClassLoader());
                DBFactory factory = (DBFactory) clz.newInstance();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Using {} implementation of org.iq80.leveldb.DBFactory", factory.getClass().getName());
                }
                return factory;
            } catch (Throwable ignored) {
            }
        }
        throw new IllegalStateException("Can't find implementation of org.iq80.leveldb.DBFactory");
    }

    @Override
    public void stop() {
        File file = getFile();

        LOG.debug("Stopping LevelDB using file: {}", file);
        if (db != null) {
            IOHelper.close(db);
            db = null;
        }
    }
}
