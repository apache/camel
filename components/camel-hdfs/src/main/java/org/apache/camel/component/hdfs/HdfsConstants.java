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

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class HdfsConstants {

    public static final int DEFAULT_PORT = 8020;

    public static final int DEFAULT_BUFFERSIZE = 4096;

    public static final short DEFAULT_REPLICATION = 3;

    public static final long DEFAULT_BLOCKSIZE = 64 * 1024 * 1024L;

    public static final HdfsCompressionType DEFAULT_COMPRESSIONTYPE = HdfsCompressionType.NONE;

    public static final HdfsCompressionCodec DEFAULT_CODEC = HdfsCompressionCodec.DEFAULT;

    public static final String DEFAULT_OPENED_SUFFIX = "opened";

    public static final String DEFAULT_READ_SUFFIX = "read";

    public static final String DEFAULT_SEGMENT_PREFIX = "seg";

    public static final long DEFAULT_DELAY = 1000L;

    public static final String DEFAULT_PATTERN = "*";

    public static final int DEFAULT_CHECK_IDLE_INTERVAL = 500;
    @Metadata(label = "producer", description = "Indicates to close the stream", javaType = "Boolean")
    public static final String HDFS_CLOSE = "CamelHdfsClose";

    public static final int DEFAULT_MAX_MESSAGES_PER_POLL = 100;
    @Metadata(description = "(producer) Specifies the name of the file to write (relative to the\n" +
                            "endpoint path). The name can be a `String` or an\n" +
                            "Expression object. Only relevant when not using a\n" +
                            "split strategy. (consumer) Specifies the name of the file to read",
              javaType = "String")
    public static final String FILE_NAME = Exchange.FILE_NAME;
    @Metadata(label = "consumer", description = "The name of the file consumed", javaType = "String")
    public static final String FILE_NAME_CONSUMED = Exchange.FILE_NAME_CONSUMED;
    @Metadata(label = "consumer", description = "The absolute path of the file", javaType = "String")
    public static final String FILE_ABSOLUTE_PATH = "CamelFileAbsolutePath";
    @Metadata(description = "The HDFS key", javaType = "Object")
    public static final String KEY = HdfsHeader.KEY.name();
    @Metadata(label = "consumer", description = "The size of the file", javaType = "Long")
    public static final String FILE_LENGTH = Exchange.FILE_LENGTH;

    private HdfsConstants() {
    }
}
