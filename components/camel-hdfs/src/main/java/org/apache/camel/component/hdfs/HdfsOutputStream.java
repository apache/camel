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

    public static HdfsOutputStream createOutputStream(String hdfsPath, HdfsConfiguration configuration) throws IOException {
        HdfsOutputStream ret = new HdfsOutputStream();
        ret.fileType = configuration.getFileType();
        ret.actualPath = hdfsPath;
        ret.info = new HdfsInfo(ret.actualPath);

        ret.suffixedPath = ret.actualPath + '.' + configuration.getOpenedSuffix();
        if (configuration.isWantAppend() || configuration.isAppend()) {
            if (!ret.info.getFileSystem().exists(new Path(ret.actualPath))) {
                configuration.setAppend(false);
            } else {
                configuration.setAppend(true);
                ret.info = new HdfsInfo(ret.suffixedPath);
                ret.info.getFileSystem().rename(new Path(ret.actualPath), new Path(ret.suffixedPath));
            }
        } else {
            if (ret.info.getFileSystem().exists(new Path(ret.actualPath))) {
                //only check if not directory
                if (!ret.info.getFileSystem().isDirectory(new Path(ret.actualPath))) {
                    if (configuration.isOverwrite()) {
                        ret.info.getFileSystem().delete(new Path(ret.actualPath), true);
                    } else {
                        throw new RuntimeCamelException("The file already exists");
                    }
                }
            }
        }
        ret.out = ret.fileType.createOutputStream(ret.suffixedPath, configuration);
        ret.opened = true;
        return ret;
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
