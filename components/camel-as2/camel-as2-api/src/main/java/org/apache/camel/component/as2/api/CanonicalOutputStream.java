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
package org.apache.camel.component.as2.api;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CanonicalOutputStream extends FilterOutputStream {

    private static byte[] newline;
    private int lastByte;

    static {
        newline = new byte[2];
        newline[0] = (byte) '\r';
        newline[1] = (byte) '\n';
    }

    private String charset;

    public CanonicalOutputStream(OutputStream out, String charset) {
        super(out);
        this.charset = charset;
        lastByte = -1;
    }

    @Override
    public void write(int i) throws IOException {
        if (i == '\r') {
            // convert all carriage-return characters into line-break sequence
            out.write(newline);
        } else if (i == '\n') {
            // convert line-feed character into line-break sequence if not
            // preceded by carriage-return
            if (lastByte != '\r') {
                out.write(newline);
            }
            // otherwise the line-feed was preceded by carriage-return so ignore it.
        } else {
            out.write(i);
        }

        lastByte = i;
    }

    @Override
    public void write(byte[] buf) throws IOException {
        this.write(buf, 0, buf.length);
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
        for (int i = off; i != off + len; i++) {
            this.write(buf[i]);
        }
    }

    public void writeln(String s) throws IOException {
        byte[] bytes = s.getBytes(charset);
        write(bytes);
        write(newline);
    }

    public void writeln() throws IOException {
        write(newline);
    }

}
