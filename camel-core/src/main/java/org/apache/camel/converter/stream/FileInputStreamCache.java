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
package org.apache.camel.converter.stream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.CipherInputStream;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.util.IOHelper;

/**
 * A {@link StreamCache} for {@link File}s
 */
public final class FileInputStreamCache extends InputStream implements StreamCache {
    private InputStream stream;
    private final File file;
    private final CipherPair ciphers;
    private final long length;
    private final FileInputStreamCache.FileInputStreamCloser closer;

    public FileInputStreamCache(File file) throws FileNotFoundException {
        this(file, null, new FileInputStreamCloser());
    }
    
    FileInputStreamCache(File file, CipherPair ciphers, FileInputStreamCloser closer) throws FileNotFoundException {
        this.file = file;
        this.stream = null;
        this.ciphers = ciphers;
        this.length = file.length();
        this.closer = closer;
        this.closer.add(this);
    }
    
    @Override
    public void close() {
        if (stream != null) {
            IOHelper.close(stream);
        }
    }

    @Override
    public void reset() {
        // reset by closing and creating a new stream based on the file
        close();
        // reset by creating a new stream based on the file
        stream = null;
        
        if (!file.exists()) {
            throw new RuntimeCamelException("Cannot reset stream from file " + file);
        }
    }

    public void writeTo(OutputStream os) throws IOException {
        if (stream == null && ciphers == null) {
            FileInputStream s = new FileInputStream(file);
            long len = file.length();
            WritableByteChannel out;
            if (os instanceof WritableByteChannel) {
                out = (WritableByteChannel)os;
            } else {
                out = Channels.newChannel(os);
            }
            FileChannel fc = s.getChannel();
            long pos = 0;
            while (pos < len) {
                long i = fc.transferTo(pos, len - pos, out);
                pos += i;
            }
            s.close();
            fc.close();
        } else {
            IOHelper.copy(getInputStream(), os);
        }
    }

    public StreamCache copy() throws IOException {
        FileInputStreamCache copy = new FileInputStreamCache(file, ciphers, closer);
        return copy;
    }

    public boolean inMemory() {
        return false;
    }

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
        InputStream in = new BufferedInputStream(new FileInputStream(file));
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
     * Collects all FileInputStreamCache instances of a temporary file which must be closed
     * at the end of the route.
     * 
     * @see CachedOutputStream
     */
    static class FileInputStreamCloser {
        
        // there can be several input streams, for example in the multi-cast parallel processing
        private List<FileInputStreamCache> fileInputStreamCaches;
        
        /** Adds a FileInputStreamCache instance to the closer.
         * <p>
         * Must be synchronized, because can be accessed by several threads. 
         */
        synchronized void add(FileInputStreamCache fileInputStreamCache) {
            if (fileInputStreamCaches == null) {
                fileInputStreamCaches = new ArrayList<FileInputStreamCache>(3);
            }
            fileInputStreamCaches.add(fileInputStreamCache);
        }
        
        void close() {
            if (fileInputStreamCaches != null) {
                for (FileInputStreamCache fileInputStreamCache : fileInputStreamCaches) {
                    fileInputStreamCache.close();
                }
                fileInputStreamCaches.clear();
            }
        }
    }

}
