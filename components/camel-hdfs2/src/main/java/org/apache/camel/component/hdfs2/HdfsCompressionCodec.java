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
package org.apache.camel.component.hdfs2;

import org.apache.hadoop.io.compress.BZip2Codec;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.io.compress.GzipCodec;

public enum HdfsCompressionCodec {

    DEFAULT {
        @Override
        public CompressionCodec getCodec() {
            return new DefaultCodec();
        }
    },

    GZIP {
        @Override
        public CompressionCodec getCodec() {
            return new GzipCodec();
        }
    },

    BZIP2 {
        @Override
        public CompressionCodec getCodec() {
            return new BZip2Codec();
        }
    };

    public abstract CompressionCodec getCodec();
    
}
