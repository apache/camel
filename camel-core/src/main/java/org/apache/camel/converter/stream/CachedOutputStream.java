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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.stream.FileInputStreamCache.TempFileManager;
import org.apache.camel.spi.StreamCachingStrategy;

/**
 * This output stream will store the content into a File if the stream context size is exceed the
 * THRESHOLD value. The default THRESHOLD value is {@link StreamCache#DEFAULT_SPOOL_THRESHOLD} bytes .
 * <p/>
 * The temp file will store in the temp directory, you can configure it by setting the TEMP_DIR property.
 * If you don't set the TEMP_DIR property, it will choose the directory which is set by the
 * system property of "java.io.tmpdir".
 * <p/>
 * You can get a cached input stream of this stream. The temp file which is created with this 
 * output stream will be deleted when you close this output stream or the cached 
 * fileInputStream(s) is/are closed after all the exchanges using the temp file are completed.
 */
public class CachedOutputStream extends OutputStream {
    @Deprecated
    public static final String THRESHOLD = "CamelCachedOutputStreamThreshold";
    @Deprecated
    public static final String BUFFER_SIZE = "CamelCachedOutputStreamBufferSize";
    @Deprecated
    public static final String TEMP_DIR = "CamelCachedOutputStreamOutputDirectory";
    @Deprecated
    public static final String CIPHER_TRANSFORMATION = "CamelCachedOutputStreamCipherTransformation";

    private final StreamCachingStrategy strategy;
    private OutputStream currentStream;
    private boolean inMemory = true;
    private int totalLength;
    private final TempFileManager tempFileManager;
    private final boolean closedOnCompletion;

    public CachedOutputStream(Exchange exchange) {
        this(exchange, true);
    }

    public CachedOutputStream(Exchange exchange, final boolean closedOnCompletion) {
        this.closedOnCompletion = closedOnCompletion;
        tempFileManager = new TempFileManager(closedOnCompletion);
        tempFileManager.addExchange(exchange);
        this.strategy = exchange.getContext().getStreamCachingStrategy();
        currentStream = new CachedByteArrayOutputStream(strategy.getBufferSize());
    }

    public void flush() throws IOException {
        currentStream.flush();       
    }

    public void close() throws IOException {
        currentStream.close();
        // need to clean up the temp file this time
        if (!closedOnCompletion) {
            tempFileManager.closeFileInputStreams();
            tempFileManager.cleanUpTempFile();
        }
    }

    public boolean equals(Object obj) {
        return currentStream.equals(obj);
    }

    public int hashCode() {
        return currentStream.hashCode();
    }

    public OutputStream getCurrentStream() {
        return currentStream;
    }

    public String toString() {
        return "CachedOutputStream[size: " + totalLength + "]";
    }

    public void write(byte[] b, int off, int len) throws IOException {
        this.totalLength += len;
        if (inMemory && currentStream instanceof ByteArrayOutputStream && strategy.shouldSpoolCache(totalLength)) {
            pageToFileStream();
        }
        currentStream.write(b, off, len);
    }

    public void write(byte[] b) throws IOException {
        this.totalLength += b.length;
        if (inMemory && currentStream instanceof ByteArrayOutputStream && strategy.shouldSpoolCache(totalLength)) {
            pageToFileStream();
        }
        currentStream.write(b);
    }

    public void write(int b) throws IOException {
        this.totalLength++;
        if (inMemory && currentStream instanceof ByteArrayOutputStream && strategy.shouldSpoolCache(totalLength)) {
            pageToFileStream();
        }
        currentStream.write(b);
    }

    public InputStream getInputStream() throws IOException {
        return (InputStream)newStreamCache();
    }    

    public InputStream getWrappedInputStream() throws IOException {
        // The WrappedInputStream will close the CachedOutputStream when it is closed
        return new WrappedInputStream(this, (InputStream)newStreamCache());
    }

    /**
     * @deprecated  use {@link #newStreamCache()}
     */
    @Deprecated
    public StreamCache getStreamCache() throws IOException {
        return newStreamCache();
    }

    /**
     * Creates a new {@link StreamCache} from the data cached in this {@link OutputStream}.
     */
    public StreamCache newStreamCache() throws IOException {
        flush();

        if (inMemory) {
            if (currentStream instanceof CachedByteArrayOutputStream) {
                return ((CachedByteArrayOutputStream) currentStream).newInputStreamCache();
            } else {
                throw new IllegalStateException("CurrentStream should be an instance of CachedByteArrayOutputStream but is: " + currentStream.getClass().getName());
            }
        } else {
            return tempFileManager.newStreamCache();
        }
    }
    

    private void pageToFileStream() throws IOException {
        flush();
        ByteArrayOutputStream bout = (ByteArrayOutputStream)currentStream;
        try {
            // creates an tmp file and a file output stream
            currentStream = tempFileManager.createOutputStream(strategy);
            bout.writeTo(currentStream);
        } finally {
            // ensure flag is flipped to file based
            inMemory = false;
        }
    }

    /**
     * @deprecated  use {@link #getStrategyBufferSize()}
     */
    @Deprecated
    public int getBufferSize() {
        return getStrategyBufferSize();
    }
    
    public int getStrategyBufferSize() {
        return strategy.getBufferSize();
    }

    // This class will close the CachedOutputStream when it is closed
    private static class WrappedInputStream extends InputStream {
        private CachedOutputStream cachedOutputStream;
        private InputStream inputStream;
        
        WrappedInputStream(CachedOutputStream cos, InputStream is) {
            cachedOutputStream = cos;
            inputStream = is;
        }
        
        @Override
        public int read() throws IOException {
            return inputStream.read();
        }
        
        @Override
        public int available() throws IOException {
            return inputStream.available();
        }
        
        @Override
        public void reset() throws IOException {
            inputStream.reset();
        }
        
        @Override
        public void close() throws IOException {
            inputStream.close();
            cachedOutputStream.close();
        }
    }

}
