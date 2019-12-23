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

import org.apache.camel.Exchange;

public enum HdfsFileType {

    NORMAL_FILE(new HdfsNormalFileHandler()),
    SEQUENCE_FILE(new HdfsSequenceFileHandler()),
    MAP_FILE(new HdfsMapFileHandler()),
    BLOOMMAP_FILE(new HdfsBloomMapFileHandler()),
    ARRAY_FILE(new HdfsArrayFileTypeHandler());

    private final HdfsFile file;

    HdfsFileType(HdfsFile file) {
        this.file = file;
    }

    public Closeable createOutputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        return this.file.createOutputStream(hdfsPath, hdfsInfoFactory);
    }

    public long append(HdfsOutputStream hdfsOutputStream, Object key, Object value, Exchange exchange) {
        return this.file.append(hdfsOutputStream, key, value, exchange);
    }

    public Closeable createInputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        return this.file.createInputStream(hdfsPath, hdfsInfoFactory);
    }

    public long next(HdfsInputStream hdfsInputStream, final Holder<Object> key, final Holder<Object> value) {
        return this.file.next(hdfsInputStream, key, value);
    }

}
