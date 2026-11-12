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
package org.apache.camel.component.jms;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.jms.JMSException;
import jakarta.jms.MessageEOFException;
import jakarta.jms.StreamMessage;

/**
 * An {@link InputStream} over the bytes of a received {@link StreamMessage}, as written by the producer: one or more
 * {@code byte[]} fields, which are read one after the other.
 */
public class StreamMessageInputStream extends InputStream {

    private final Lock lock = new ReentrantLock();
    private final StreamMessage message;
    private volatile boolean eof;

    public StreamMessageInputStream(StreamMessage message) {
        this.message = message;
    }

    @Override
    public int read() throws IOException {
        // the message holds byte[] fields, which cannot be read with readByte, so read one byte through the array
        byte[] single = new byte[1];
        int num = read(single, 0, 1);
        return num < 0 ? -1 : single[0] & 0xFF;
    }

    @Override
    public int read(byte[] array) throws IOException {
        return read(array, 0, array.length);
    }

    @Override
    public int read(byte[] array, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, array.length);
        if (len == 0) {
            return 0;
        }
        if (off == 0 && len == array.length) {
            return doRead(array);
        }
        // readBytes always fills from the start of the array, so read into a buffer and copy it to the offset
        byte[] buffer = new byte[len];
        int num = doRead(buffer);
        if (num > 0) {
            System.arraycopy(buffer, 0, array, off, num);
        }
        return num;
    }

    private int doRead(byte[] array) throws IOException {
        if (eof) {
            return -1;
        }
        try {
            int num = message.readBytes(array);
            if (num <= 0) {
                // the end of the current byte[] field (the producer writes the payload in chunks)
                // so continue with the next field, if there is one
                num = message.readBytes(array);
            }
            eof = num < 0;
            return num;
        } catch (MessageEOFException e) {
            eof = true;
            return -1;
        } catch (JMSException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void reset() throws IOException {
        lock.lock();
        try {
            message.reset();
            eof = false;
        } catch (JMSException e) {
            throw new IOException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int available() throws IOException {
        // if we are end of file then there is no more data, otherwise assume there is at least one more byte
        return eof ? 0 : 1;
    }
}
