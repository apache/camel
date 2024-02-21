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
package org.apache.camel.util.xml;

import java.io.ByteArrayInputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import javax.xml.transform.stream.StreamSource;

/**
 * A helper class which provides a JAXP {@link javax.xml.transform.Source Source} from a String which can be read as
 * many times as required. Encoding is default UTF-8.
 */
public class StringSource extends StreamSource implements Externalizable {
    private String text;
    private String encoding = "UTF-8";

    public StringSource() {
    }

    public StringSource(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must be specified");
        }
        this.text = text;
    }

    public StringSource(String text, String systemId) {
        this(text);
        if (systemId == null) {
            throw new IllegalArgumentException("systemId must be specified");
        }
        setSystemId(systemId);
    }

    public StringSource(String text, String systemId, String encoding) {
        this(text, systemId);
        if (encoding == null) {
            throw new IllegalArgumentException("encoding must be specified");
        }
        this.encoding = encoding;
    }

    @Override
    public boolean isEmpty() {
        return text.isEmpty();
    }

    @Override
    public InputStream getInputStream() {
        try {
            return new ByteArrayInputStream(text.getBytes(encoding));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Reader getReader() {
        return new StringReader(text);
    }

    @Override
    public String toString() {
        return "StringSource[" + text + "]";
    }

    public String getText() {
        return text;
    }

    public String getEncoding() {
        return encoding;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        int b = (text != null ? 0x01 : 0x00) + (encoding != null ? 0x02 : 0x00) + (getPublicId() != null ? 0x04 : 0x00)
                + (getSystemId() != null ? 0x08 : 0x00);
        out.writeByte(b);
        if ((b & 0x01) != 0) {
            out.writeUTF(text);
        }
        if ((b & 0x02) != 0) {
            out.writeUTF(encoding);
        }
        if ((b & 0x04) != 0) {
            out.writeUTF(getPublicId());
        }
        if ((b & 0x08) != 0) {
            out.writeUTF(getSystemId());
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int b = in.readByte();
        if ((b & 0x01) != 0) {
            text = in.readUTF();
        }
        if ((b & 0x02) != 0) {
            encoding = in.readUTF();
        }
        if ((b & 0x04) != 0) {
            setPublicId(in.readUTF());
        }
        if ((b & 0x08) != 0) {
            setSystemId(in.readUTF());
        }
    }
}
