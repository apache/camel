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
package org.apache.camel.converter.stream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link StreamCache} for {@link File}s
 */
public final class FileInputStreamCache extends InputStream implements StreamCache {
    private InputStream stream;
    private final long length;
    private final FileInputStreamCache.TempFileManager tempFileManager;
    private final File file;
    private final CipherPair ciphers;

    /** Only for testing purposes.*/
    public FileInputStreamCache(File file) throws FileNotFoundException {
        this(new TempFileManager(file, true));
    }
    
    FileInputStreamCache(TempFileManager closer) throws FileNotFoundException {
        this.file = closer.getTempFile();
        this.stream = null;
        this.ciphers = closer.getCiphers();
        this.length = file.length();
        this.tempFileManager = closer;
        this.tempFileManager.add(this);
    }
    
    @Override
    public void close() {
        if (stream != null) {
            IOHelper.close(stream);
        }
    }

    @Override
    public synchronized void reset() {
        // reset by closing and creating a new stream based on the file
        close();
        // reset by creating a new stream based on the file
        stream = null;
        
        if (!file.exists()) {
            throw new RuntimeCamelException("Cannot reset stream from file " + file);
        }
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        if (stream == null && ciphers == null) {
            Files.copy(file.toPath(), os);
        } else {
            IOHelper.copy(getInputStream(), os);
        }
    }

    @Override
    public StreamCache copy(Exchange exchange) throws IOException {
        tempFileManager.addExchange(exchange);
        FileInputStreamCache copy = new FileInputStreamCache(tempFileManager);
        return copy;
    }

    @Override
    public boolean inMemory() {
        return false;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public int available() throws IOException {
        return getInputStream().available();
    }

    @Override
    public int read() throws IOException {
        return getInputStream().read();
    }

    protected InputStream getInputStream() throws IOException {
        if (stream == null) {
            stream = createInputStream(file);
        }
        return stream;
    }

    private InputStream createInputStream(File file) throws IOException {
        InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath(), StandardOpenOption.READ));
        if (ciphers != null) {
            in = new CipherInputStream(in, ciphers.getDecryptor()) {
                boolean closed;
                public void close() throws IOException {
                    if (!closed) {
                        super.close();
                        closed = true;
                    }
                }
            };
        }
        return in;
    }

    /** 
     * Manages the temporary file for the file input stream caches.
     * 
     * Collects all FileInputStreamCache instances of the temporary file.
     * Counts the number of exchanges which have a FileInputStreamCache  instance of the temporary file.
     * Deletes the temporary file, if all exchanges are done.
     * 
     * @see CachedOutputStream
     */
    static class TempFileManager {
        
        private static final Logger LOG = LoggerFactory.getLogger(TempFileManager.class);
        /** Indicator whether the file input stream caches are closed on completion of the exchanges. */
        private final boolean closedOnCompletion;
        private AtomicInteger exchangeCounter = new AtomicInteger();
        private File tempFile;
        private OutputStream outputStream; // file output stream
        private CipherPair ciphers;
        
        // there can be several input streams, for example in the multi-cast, or wiretap parallel processing
        private List<FileInputStreamCache> fileInputStreamCaches;

        /** Only for testing.*/
        private TempFileManager(File file, boolean closedOnCompletion) {
            this(closedOnCompletion);
            this.tempFile = file;
        }
        
        TempFileManager(boolean closedOnCompletion) {
            this.closedOnCompletion = closedOnCompletion;
        }
                
        /** Adds a FileInputStreamCache instance to the closer.
         * <p>
         * Must be synchronized, because can be accessed by several threads. 
         */
        synchronized void add(FileInputStreamCache fileInputStreamCache) {
            if (fileInputStreamCaches == null) {
                fileInputStreamCaches = new ArrayList<>(3);
            }
            fileInputStreamCaches.add(fileInputStreamCache);
        }
        
        void addExchange(Exchange exchange) {
            if (closedOnCompletion) {
                exchangeCounter.incrementAndGet();
                // add on completion so we can cleanup after the exchange is done such as deleting temporary files
                Synchronization onCompletion = new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        int actualExchanges = exchangeCounter.decrementAndGet();
                        if (actualExchanges == 0) {
                            // only one exchange (one thread) left, therefore we must not synchronize the following lines of code
                            try {                              
                                closeFileInputStreams();
                                if (outputStream != null) {
                                    outputStream.close();
                                }
                            } catch (Exception e) {
                                LOG.warn("Error closing streams. This exception will be ignored.", e);
                            }
                            try {
                                cleanUpTempFile();
                            } catch (Exception e) {
                                LOG.warn("Error deleting temporary cache file: " + tempFile + ". This exception will be ignored.", e);
                            }
                        }
                    }

                    @Override
                    public String toString() {
                        return "OnCompletion[CachedOutputStream]";
                    }
                };
                UnitOfWork streamCacheUnitOfWork = exchange.getProperty(Exchange.STREAM_CACHE_UNIT_OF_WORK, UnitOfWork.class);
                if (streamCacheUnitOfWork != null) {
                    // The stream cache must sometimes not be closed when the exchange is deleted. This is for example the
                    // case in the splitter and multi-cast case with AggregationStrategy where the result of the sub-routes
                    // are aggregated later in the main route. Here, the cached streams of the sub-routes must be closed with
                    // the Unit of Work of the main route.
                    streamCacheUnitOfWork.addSynchronization(onCompletion);
                } else {
                    // add on completion so we can cleanup after the exchange is done such as deleting temporary files
                    exchange.adapt(ExtendedExchange.class).addOnCompletion(onCompletion);
                }
            }
        }
        
        OutputStream createOutputStream(StreamCachingStrategy strategy) throws IOException {
            // should only be called once
            if (tempFile != null) {
                throw new IllegalStateException("The method 'createOutputStream' can only be called once!");
            }
            tempFile = FileUtil.createTempFile("cos", ".tmp", strategy.getSpoolDirectory());

            LOG.trace("Creating temporary stream cache file: {}", tempFile);
            OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE));
            if (ObjectHelper.isNotEmpty(strategy.getSpoolCipher())) {
                try {
                    if (ciphers == null) {
                        ciphers = new CipherPair(strategy.getSpoolCipher());
                    }
                } catch (GeneralSecurityException e) {
                    throw new IOException(e.getMessage(), e);
                }
                out = new CipherOutputStream(out, ciphers.getEncryptor()) {
                    boolean closed;
                    public void close() throws IOException {
                        if (!closed) {
                            super.close();
                            closed = true;
                        }
                    }
                };
            }
            outputStream = out;
            return out;
        }
        
        FileInputStreamCache newStreamCache() throws IOException {
            try {
                return new FileInputStreamCache(this);
            } catch (FileNotFoundException e) {
                throw new IOException("Cached file " + tempFile + " not found", e);
            }
        }
        
        void closeFileInputStreams() {
            if (fileInputStreamCaches != null) {
                for (FileInputStreamCache fileInputStreamCache : fileInputStreamCaches) {
                    fileInputStreamCache.close();
                }
                fileInputStreamCaches.clear();
            }
        } 

        void cleanUpTempFile() {
            // cleanup temporary file
            try {
                if (tempFile != null) {
                    FileUtil.deleteFile(tempFile);
                    tempFile = null;
                }
            } catch (Exception e) {
                LOG.warn("Error deleting temporary cache file: " + tempFile + ". This exception will be ignored.", e);
            }
        }
        
        File getTempFile() {
            return tempFile;
        }
        
        CipherPair getCiphers() {
            return ciphers;
        }
        
    }

}
