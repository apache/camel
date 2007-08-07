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
package org.apache.camel.converter.jaxp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import javax.xml.transform.stream.StreamSource;

/**
 * A helper class which provides a JAXP {@link Source} from a String which can
 * be read as many times as required.
 * 
 * @version $Revision$
 */
public class StringSource extends StreamSource implements Serializable {

    private final String text;
    private String encoding = "UTF-8";

    public StringSource(String text) {
        if (text == null) {
            throw new NullPointerException("text can not be null");
        }
        this.text = text;
    }

    public StringSource(String text, String systemId) {
        this(text);
        setSystemId(systemId);
    }

    public StringSource(String text, String systemId, String encoding) {
        this.text = text;
        this.encoding = encoding;
        setSystemId(systemId);
    }

    public InputStream getInputStream() {
        try {
            return new ByteArrayInputStream(text.getBytes(encoding));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public Reader getReader() {
        return new StringReader(text);
    }

    public String toString() {
        return "StringSource[" + text + "]";
    }

    public String getText() {
        return text;
    }

}
