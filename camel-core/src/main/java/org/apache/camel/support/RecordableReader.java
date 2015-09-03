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
package org.apache.camel.support;

import java.io.CharArrayWriter;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

/**
 * This class is used by the tokenizer to extract data while reading from the stream.
 * TODO it is used package internally but may be moved to some common package.
 */
class RecordableReader extends FilterReader {
    private TrimmableCharArrayWriter buf;
    private boolean recording;
    protected RecordableReader(Reader in) {
        super(in);
        this.buf = new TrimmableCharArrayWriter();
        this.recording = true;
    }

    @Override
    public int read() throws IOException {
        int c = super.read();
        if (c > 0 && recording) {
            buf.write(c);
        }
        return c;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int n = super.read(cbuf, off, len);
        if (n > 0 && recording) {
            buf.write(cbuf, off, n);
        }
        return n;
    }

    public String getText(int pos) {
        recording = false;
        String t = new String(buf.getCharArray(), 0, pos);
        buf.trim(pos, 0);
        return t;
    }
    
    public char[] getChars(int pos) {
        recording = false;
        char[] b = buf.toCharArray(pos);
        buf.trim(pos, 0);
        return b;
    }
    
    public void record() {
        recording = true;
    }

    int size() {
        return buf.size();
    }

    private static class TrimmableCharArrayWriter extends CharArrayWriter {
        public void trim(int head, int tail) {
            System.arraycopy(buf, head, buf, 0, count - head - tail);
            count -= head + tail;
        }
        
        public char[] toCharArray(int len) {
            char[] b = new char[len];
            System.arraycopy(buf, 0, b, 0, len);
            return b;
        }

        char[] getCharArray() {
            return buf;
        }
    }

}
