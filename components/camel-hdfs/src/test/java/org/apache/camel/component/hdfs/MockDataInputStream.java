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

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hadoop.fs.FSExceptionMessages;
import org.apache.hadoop.fs.FSInputStream;
import org.apache.hadoop.fs.PositionedReadable;
import org.apache.hadoop.fs.Seekable;

public class MockDataInputStream extends FSInputStream implements Seekable, PositionedReadable {

    private final FileInputStream fis;
    private long position;

    MockDataInputStream(String targetFile) throws FileNotFoundException {
        this(new FileInputStream(targetFile));
    }

    MockDataInputStream(FileInputStream fis) {
        this.fis = fis;
    }

    @Override
    public void seek(long pos) throws IOException {
        if (pos < 0) {
            throw new EOFException(
                    FSExceptionMessages.NEGATIVE_SEEK);
        }
        fis.getChannel().position(pos);
        this.position = pos;
    }

    @Override
    public long getPos() throws IOException {
        return this.position;
    }

    @Override
    public boolean seekToNewSource(long targetPos) throws IOException {
        return false;
    }

    @Override
    public int available() throws IOException {
        return fis.available();
    }

    @Override
    public void close() throws IOException {
        fis.close();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read() throws IOException {
        try {
            int value = fis.read();
            if (value >= 0) {
                this.position++;
            }
            return value;
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            int value = fis.read(b, off, len);
            if (value > 0) {
                this.position += value;
            }
            return value;
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    @Override
    public int read(long position, byte[] b, int off, int len)
            throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(b, off, len);
        try {
            return fis.getChannel().read(bb, position);
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    @Override
    public long skip(long n) throws IOException {
        long value = fis.skip(n);
        if (value > 0) {
            this.position += value;
        }
        return value;
    }

}
