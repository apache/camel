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
package org.apache.camel.component.hdfs;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import javax.xml.ws.Holder;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;

public class HdfsInputStream {

    private HdfsFileType fileType;
    private String actualPath;
    private String suffixedPath;
    private Closeable in;
    private boolean opened;
    private int chunkSize;
    private final AtomicLong numOfReadBytes = new AtomicLong(0L);
    private final AtomicLong numOfReadMessages = new AtomicLong(0L);

    protected HdfsInputStream() {
    }

    public static HdfsInputStream createInputStream(String hdfsPath, HdfsConfiguration configuration) throws IOException {
        HdfsInputStream ret = new HdfsInputStream();
        ret.fileType = configuration.getFileType();
        ret.actualPath = hdfsPath;
        ret.suffixedPath = ret.actualPath + '.' + configuration.getOpenedSuffix();
        ret.chunkSize = configuration.getChunkSize();
        HdfsInfo info = new HdfsInfo(ret.actualPath);
        info.getFileSystem().rename(new Path(ret.actualPath), new Path(ret.suffixedPath));
        ret.in = ret.fileType.createInputStream(ret.suffixedPath, configuration);
        ret.opened = true;
        return ret;
    }

    public final void close() throws IOException {
        if (opened) {
            IOUtils.closeStream(in);
            HdfsInfo info = new HdfsInfo(actualPath);
            info.getFileSystem().rename(new Path(suffixedPath), new Path(actualPath + '.' + HdfsConstants.DEFAULT_READ_SUFFIX));
            opened = false;
        }
    }

    public final long next(Holder<Object> key, Holder<Object> value) {
        long nb = fileType.next(this, key, value);
        if (nb > 0) {
            numOfReadBytes.addAndGet(nb);
            numOfReadMessages.incrementAndGet();
        }
        return nb;
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

}