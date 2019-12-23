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
package org.apache.camel.component.hdfs;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.RuntimeCamelException;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HdfsInputStream implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(HdfsInputStream.class);

    private HdfsFileType fileType;
    private HdfsInfo info;
    private String actualPath;
    private String suffixedPath;
    private String suffixedReadPath;
    private Closeable in;
    private boolean opened;
    private int chunkSize;
    private final AtomicLong numOfReadBytes = new AtomicLong(0L);
    private final AtomicLong numOfReadMessages = new AtomicLong(0L);

    private boolean streamDownload;

    private EntryHolder cachedNextEntry;

    protected HdfsInputStream() {
    }

    /**
     *
     * @param hdfsPath
     * @param hdfsInfoFactory
     * @return
     */
    public static HdfsInputStream createInputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        HdfsConfiguration endpointConfig = hdfsInfoFactory.getEndpointConfig();
        HdfsInputStream iStream = new HdfsInputStream();
        iStream.fileType = endpointConfig.getFileType();
        iStream.actualPath = hdfsPath;
        iStream.suffixedPath = iStream.actualPath + '.' + endpointConfig.getOpenedSuffix();
        iStream.suffixedReadPath = iStream.actualPath + '.' + endpointConfig.getReadSuffix();
        iStream.chunkSize = endpointConfig.getChunkSize();
        iStream.streamDownload = endpointConfig.isStreamDownload();
        try {
            iStream.info = hdfsInfoFactory.newHdfsInfo(iStream.actualPath);
            if (iStream.info.getFileSystem().rename(new Path(iStream.actualPath), new Path(iStream.suffixedPath))) {
                iStream.in = iStream.fileType.createInputStream(iStream.suffixedPath, hdfsInfoFactory);
                iStream.opened = true;
            } else {
                LOG.debug("Failed to open file [{}] because it doesn't exist", hdfsPath);
                iStream = null;
            }
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        }

        return iStream;
    }

    @Override
    public final void close() throws IOException {
        if (opened) {
            IOUtils.closeStream(in);
            info.getFileSystem().rename(new Path(suffixedPath), new Path(suffixedReadPath));
            opened = false;
        }
    }

    /**
     * Reads next record/chunk specific to give file type.
     * @param key
     * @param value
     * @return number of bytes read. 0 is correct number of bytes (empty file), -1 indicates no record was read
     */
    public final long next(final Holder<Object> key, final Holder<Object> value) {
        EntryHolder nextEntry = Optional.ofNullable(cachedNextEntry).orElseGet(() -> getNextFromStream(key, value));
        cachedNextEntry = null;

        key.setValue(nextEntry.getKey().getValue());
        value.setValue(nextEntry.getValue().getValue());

        return nextEntry.getByteCount();
    }

    private EntryHolder getNextFromStream(final Holder<Object> key, final Holder<Object> value) {
        long nb = fileType.next(this, key, value);
        // when zero bytes was read from given type of file, we may still have a record (e.g., empty file)
        // null value.value is the only indication that no (new) record/chunk was read
        if ((nb == 0 && numOfReadMessages.get() > 0) || Objects.isNull(value.getValue())) {
            // we've read all chunks from file, which size is exact multiple the chunk size
            nb = -1;
        } else {
            numOfReadBytes.addAndGet(nb);
            numOfReadMessages.incrementAndGet();
        }

        return new EntryHolder(key, value, nb);
    }

    /**
     */
    public final boolean hasNext() {
        if (Objects.isNull(cachedNextEntry)) {
            Holder<Object> nextKey = new Holder<>();
            Holder<Object> nextValue = new Holder<>();
            long nextByteCount = next(nextKey, nextValue);
            cachedNextEntry = new EntryHolder(nextKey, nextValue, nextByteCount);
        }

        return cachedNextEntry.hasNext();
    }

    public final long getNumOfReadBytes() {
        return numOfReadBytes.longValue();
    }

    public final long getNumOfReadMessages() {
        return numOfReadMessages.longValue();
    }

    public final String getActualPath() {
        return actualPath;
    }

    public final int getChunkSize() {
        return chunkSize;
    }

    public final Closeable getIn() {
        return in;
    }

    public boolean isOpened() {
        return opened;
    }

    public boolean isStreamDownload() {
        return streamDownload;
    }

    private static class EntryHolder {

        private long byteCount;
        private Holder<Object> key;
        private Holder<Object> value;

        public EntryHolder(Holder<Object> key, Holder<Object> value, long byteCount) {
            this.key = key;
            this.value = value;
            this.byteCount = byteCount;
        }

        public Holder<Object> getKey() {
            return key;
        }

        public Holder<Object> getValue() {
            return value;
        }

        public Boolean hasNext() {
            return byteCount >= 0;
        }

        public long getByteCount() {
            return byteCount;
        }
    }
}
