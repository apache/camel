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

import org.apache.camel.TypeConverter;

public enum HdfsFileType {

    NORMAL_FILE(new HdfsNormalFileType()),
    SEQUENCE_FILE(new HdfsSequenceFileType()),
    MAP_FILE(new HdfsMapFileType()),
    BLOOMMAP_FILE(new HdfsBloommapFileType()),
    ARRAY_FILE(new HdfsArrayFileType());

    private final HdfsFile file;

    private HdfsFileType(HdfsFile file) {
        this.file = file;
    }

    public long append(HdfsOutputStream hdfsostr, Object key, Object value, TypeConverter typeConverter) {
        return this.file.append(hdfsostr, key, value, typeConverter);
    }

    public long next(HdfsInputStream hdfsInputStream, Holder<Object> key, Holder<Object> value) {
        return this.file.next(hdfsInputStream, key, value);
    }

    public Closeable createOutputStream(String hdfsPath, HdfsConfiguration configuration) {
        return this.file.createOutputStream(hdfsPath, configuration);
    }

    public Closeable createInputStream(String hdfsPath, HdfsConfiguration configuration) {
        return this.file.createInputStream(hdfsPath, configuration);
    }

}
