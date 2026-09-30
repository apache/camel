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
package org.apache.camel.support.builder;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * This class is used internally by the tokenizer to extract data while reading from the stream.
 */
class RecordableInputStream extends FilterInputStream {
    private final TrimmableByteArrayOutputStream buf;
    private final String charset;
    private boolean recording;

    RecordableInputStream(InputStream in, String charset) {
        super(in);
        this.buf = new TrimmableByteArrayOutputStream();
        this.charset = charset;
        this.recording = true;
    }

    @Override
    public int read() throws IOException {
        int c = super.read();
        if (c >= 0 && recording) {
            buf.write(c);
        }
        return c;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0 && recording) {
            buf.write(b, off, n);
        }
        return n;
    }

    /**
     * Returns the recorded text before the given position, and stops recording.
     *
     * @param pos the position in characters (not bytes) as the scanner reports it
     */
    public String getText(int pos) {
        String t = null;
        int len = 0;
        recording = false;
        try {
            // decode what was recorded and cut by characters, as a character may take more than one byte
            if (charset == null) {
                t = new String(buf.getByteArray(), 0, buf.size());
                t = t.substring(0, Math.min(pos, t.length()));
                len = t.getBytes().length;
            } else {
                t = new String(buf.getByteArray(), 0, buf.size(), charset);
                t = t.substring(0, Math.min(pos, t.length()));
                len = t.getBytes(charset).length;
            }
        } catch (UnsupportedEncodingException e) {
            // ignore it as this encoding exception should have been caught earlier while scanning.
        } finally {
            // keep what was recorded after the text
            buf.trim(Math.min(len, buf.size()), 0);
        }

        return t;
    }

    public byte[] getBytes(int pos) {
        recording = false;
        byte[] b = buf.toByteArray(pos);
        buf.trim(pos, 0);
        return b;
    }

    public void record() {
        recording = true;
    }

    int size() {
        return buf.size();
    }

    private static class TrimmableByteArrayOutputStream extends ByteArrayOutputStream {
        public void trim(int head, int tail) {
            System.arraycopy(buf, head, buf, 0, count - head - tail);
            count -= head + tail;
        }

        public byte[] toByteArray(int len) {
            byte[] b = new byte[len];
            System.arraycopy(buf, 0, b, 0, len);
            return b;
        }

        byte[] getByteArray() {
            return buf;
        }
    }

}
