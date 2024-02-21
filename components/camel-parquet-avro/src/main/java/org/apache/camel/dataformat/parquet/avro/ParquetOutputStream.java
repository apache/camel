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
package org.apache.camel.dataformat.parquet.avro;

import java.io.BufferedOutputStream;
import java.io.IOException;

import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;

public class ParquetOutputStream implements OutputFile {

    private final String streamId;
    private final BufferedOutputStream out;

    private int pos;

    public ParquetOutputStream(String streamId, BufferedOutputStream out) {
        this.pos = 0;
        this.streamId = streamId;
        this.out = out;
    }

    @Override
    public PositionOutputStream create(long blockSizeHint) throws IOException {
        return createPositionOutputstream();
    }

    private PositionOutputStream createPositionOutputstream() {
        return new PositionOutputStream() {

            @Override
            public long getPos() throws IOException {
                return pos;
            }

            @Override
            public void flush() throws IOException {
                out.flush();
            };

            @Override
            public void close() throws IOException {
                out.close();
            };

            @Override
            public void write(int b) throws IOException {
                out.write(b);
                pos++;
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
                pos += len;
            }
        };
    }

    @Override
    public PositionOutputStream createOrOverwrite(long blockSizeHint) throws IOException {
        return createPositionOutputstream();
    }

    @Override
    public boolean supportsBlockSize() {
        return false;
    }

    @Override
    public long defaultBlockSize() {
        return 0;
    }

    @Override
    public String toString() {
        return "ParquetOutputStream[" + streamId + "]";
    }
}
