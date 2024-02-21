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

import jakarta.jms.JMSException;
import jakarta.jms.MessageEOFException;
import jakarta.jms.StreamMessage;

public class StreamMessageInputStream extends InputStream {

    private final StreamMessage message;
    private volatile boolean eof;

    public StreamMessageInputStream(StreamMessage message) {
        this.message = message;
    }

    @Override
    public int read() throws IOException {
        try {
            return message.readByte();
        } catch (MessageEOFException e) {
            eof = true;
            return -1;
        } catch (JMSException e) {
            throw new IOException(e);
        }
    }

    @Override
    public int read(byte[] array) throws IOException {
        return doRead(array);
    }

    private int doRead(byte[] array) throws IOException {
        try {
            int num = message.readBytes(array);
            if (num < 0) {
                //the first 128K(FileUtil.BUFFER_SIZE/128K is used when sending JMS StreamMessage)
                //buffer reached, give a chance to see if there is the next 128K buffer
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
    public int read(byte[] array, int off, int len) throws IOException {
        // we cannot honor off and len, but assuming off is always 0
        return doRead(array);
    }

    @Override
    public synchronized void reset() throws IOException {
        try {
            message.reset();
        } catch (JMSException e) {
            throw new IOException(e);
        }
    }

    @Override
    public int available() throws IOException {
        // if we are end of file then there is no more data, otherwise assume there is at least one more byte
        return eof ? 0 : 1;
    }
}
