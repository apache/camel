/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * Copyright (c)  2000-2019, TradeChannel AB. All rights reserved.
 * Any right to utilize the System under this Agreement shall be subject to the terms and condition of the
 * License Agreement between Customer "X" and TradeChannel AB.
 *
 * TradeseC contains third party software which includes software owned or licensed by a third party and
 * sub licensed to the Customer by TradeChannel AB in accordance with the License Agreement.
 *
 * TradeChannel AB owns the rights to the software product TradeseC.
 *
 * TradeChannel AB grants a right to the Customer and the Customer accepts a non-exclusive,
 * non-transferrable license to use TradeseC and Third Party Software, in accordance with the conditions
 * specified in this License Agreement.
 *
 * The Customer may not use TradeseC or the Third Party Software for time-sharing, rental,
 * service bureau use, or similar use. The Customer is responsible for that all use of TradeseC
 * and the Third Party Software is in accordance with the License Agreement.
 *
 * The Customer may not transfer, sell, sublicense, let, lend or in any other way permit any person or entity
 * other than the Customer, avail himself, herself or itself of or otherwise any rights to TradeseC or the
 * Third Party Software, either directly or indirectly.
 *
 * The Customer may not use, copy, modify or in any other way transfer or use TradeseC or the
 * Third Party Software wholly or partially, nor allow another person or entity to do so, in any way other than
 * what is expressly permitted according to the License Agreement. Nor, consequently, may the Customer,
 * independently or through an agent, reverse engineer, decompile or disassemble TradeseC, the Third Party Software
 * or any accessories that may be related to it.
 *
 * The Customer acknowledges TradeseC <i>(including but not limited to any copyrights, trademarks,
 * documentation, enhancements or other intellectual property or proprietary rights relating to it)</i>
 * and Third Party Software is the proprietary material of the Supplier and respectively Third Party.
 *
 * The Third Party Software are protected by copyright law.
 *
 * The Customer shall not remove, erase or hide from view any information about a patent, copyright,
 * trademark, confidentiality notice, mark or legend appearing on any of TradeseC or Third Party Software,
 * any medium by which they are made available or any form of output produced by them.
 *
 * The License Agreement will only grant the Customer the right to use TradeseC and Third Party Software
 * under the terms of the License Agreement.
 */
package org.apache.camel.component.leveldb;

import org.apache.camel.Service;
import org.apache.camel.util.IOHelper;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

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

    public void setFile(final File file) throws IOException {
        this.file = file;
    }

    public String getFileName() throws IOException {
        return file.getCanonicalPath();
    }

    public void setFileName(final String fileName) {
        this.file = new File(fileName);
    }

    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    public void setWriteBufferSize(final int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
    }

    public int getMaxOpenFiles() {
        return maxOpenFiles;
    }

    public void setMaxOpenFiles(final int maxOpenFiles) {
        this.maxOpenFiles = maxOpenFiles;
    }

    public int getBlockRestartInterval() {
        return blockRestartInterval;
    }

    public void setBlockRestartInterval(final int blockRestartInterval) {
        this.blockRestartInterval = blockRestartInterval;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(final int blockSize) {
        this.blockSize = blockSize;
    }

    public String getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(final String compressionType) {
        this.compressionType = compressionType;
    }

    public boolean isVerifyChecksums() {
        return verifyChecksums;
    }

    public void setVerifyChecksums(final boolean verifyChecksums) {
        this.verifyChecksums = verifyChecksums;
    }

    public boolean isParanoidChecks() {
        return paranoidChecks;
    }

    public void setParanoidChecks(final boolean paranoidChecks) {
        this.paranoidChecks = paranoidChecks;
    }

    public long getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(final long cacheSize) {
        this.cacheSize = cacheSize;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(final boolean sync) {
        this.sync = sync;
    }

    public WriteOptions getWriteOptions() {
        final WriteOptions options = new WriteOptions();
        options.sync(sync);
        return options;
    }

    public void start() {
        if (getFile() == null) {
            throw new IllegalArgumentException("A file must be configured");
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting LevelDB using file: {}", getFile());
        }

        final Options options = new Options().writeBufferSize(writeBufferSize).maxOpenFiles(maxOpenFiles)
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
            db = factory.open(getFile(), options);
        } catch (final IOException ioe) {
            throw new RuntimeException("Error opening LevelDB with file " + getFile(), ioe);
        }
    }

    public void stop() {
        final File file = getFile();

        LOG.debug("Stopping LevelDB using file: {}", file);
        if (db != null) {
            IOHelper.close(db);
            db = null;
        }
    }
}
