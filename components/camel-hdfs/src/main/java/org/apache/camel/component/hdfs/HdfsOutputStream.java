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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;

public class HdfsOutputStream implements Closeable {

    private HdfsFileType fileType;
    private HdfsInfo info;
    private String actualPath;
    private String suffixedPath;
    private Closeable out;
    private volatile boolean opened;
    private final AtomicLong numOfWrittenBytes = new AtomicLong(0L);
    private final AtomicLong numOfWrittenMessages = new AtomicLong(0L);
    private final AtomicLong lastAccess = new AtomicLong(Long.MAX_VALUE);
    private final AtomicBoolean busy = new AtomicBoolean(false);

    protected HdfsOutputStream() {
    }

    public static HdfsOutputStream createOutputStream(String hdfsPath, HdfsConfiguration configuration, HdfsInfoFactory hdfsInfoFactory) throws IOException {
        HdfsOutputStream oStream = new HdfsOutputStream();
        oStream.fileType = configuration.getFileType();
        oStream.actualPath = hdfsPath;
        oStream.info = hdfsInfoFactory.newHdfsInfoWithoutAuth(oStream.actualPath);

        oStream.suffixedPath = oStream.actualPath + '.' + configuration.getOpenedSuffix();

        Path actualPath = new Path(oStream.actualPath);
        boolean actualPathExists = oStream.info.getFileSystem().exists(actualPath);

        if (configuration.isWantAppend() || configuration.isAppend()) {
            if (actualPathExists) {
                configuration.setAppend(true);
                oStream.info = hdfsInfoFactory.newHdfsInfoWithoutAuth(oStream.suffixedPath);
                oStream.info.getFileSystem().rename(actualPath, new Path(oStream.suffixedPath));
            } else {
                configuration.setAppend(false);
            }
        } else if (actualPathExists && !oStream.info.getFileSystem().isDirectory(actualPath)) { // only check if not directory
            if (configuration.isOverwrite()) {
                oStream.info.getFileSystem().delete(actualPath, true);
            } else {
                throw new RuntimeCamelException("The file already exists");
            }
        }

        oStream.out = oStream.fileType.createOutputStream(oStream.suffixedPath, configuration);
        oStream.opened = true;
        return oStream;
    }

    @Override
    public void close() throws IOException {
        if (opened) {
            IOUtils.closeStream(out);
            info.getFileSystem().rename(new Path(suffixedPath), new Path(actualPath));
            opened = false;
        }
    }

    public void append(Object key, Object value, TypeConverter typeConverter) {
        try {
            busy.set(true);
            long nb = fileType.append(this, key, value, typeConverter);
            numOfWrittenBytes.addAndGet(nb);
            numOfWrittenMessages.incrementAndGet();
            lastAccess.set(System.currentTimeMillis());
        } finally {
            busy.set(false);
        }
    }

    public long getNumOfWrittenBytes() {
        return numOfWrittenBytes.longValue();
    }

    public long getNumOfWrittenMessages() {
        return numOfWrittenMessages.longValue();
    }

    public long getLastAccess() {
        return lastAccess.longValue();
    }

    public String getActualPath() {
        return actualPath;
    }

    public AtomicBoolean isBusy() {
        return busy;
    }

    public Closeable getOut() {
        return out;
    }
}
