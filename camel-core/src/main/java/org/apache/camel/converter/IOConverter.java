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
package org.apache.camel.converter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;

import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Some core java.io based <a
 * href="http://activemq.apache.org/camel/type-converter.html">Type Converters</a>
 *
 * @version $Revision$
 */
@Converter
public final class IOConverter {
    private static final transient Log LOG = LogFactory.getLog(IOConverter.class);
    private static XmlConverter xmlConverter;

    /**
     * Utility classes should not have a public constructor.
     */
    private IOConverter() {
    }

    @Converter
    public static InputStream toInputStream(URL url) throws IOException {
        return url.openStream();
    }

    @Converter
    public static InputStream toInputStream(File file) throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    @Converter
    public static BufferedReader toReader(File file) throws FileNotFoundException {
        return new BufferedReader(new FileReader(file));
    }

    @Converter
    public static File toFile(String name) throws FileNotFoundException {
        return new File(name);
    }

    @Converter
    public static OutputStream toOutputStream(File file) throws FileNotFoundException {
        return new BufferedOutputStream(new FileOutputStream(file));
    }

    @Converter
    public static BufferedWriter toWriter(File file) throws IOException {
        return new BufferedWriter(new FileWriter(file));
    }

    @Converter
    public static Reader toReader(InputStream in) throws FileNotFoundException {
        return new InputStreamReader(in);
    }

    @Converter
    public static Writer toWriter(OutputStream out) throws FileNotFoundException {
        return new OutputStreamWriter(out);
    }

    @Converter
    public static StringReader toReader(String text) {
        return new StringReader(text);
    }

    @Converter
    public static InputStream toInputStream(String text, Exchange exchange) {
        if (exchange != null) {
            String charsetName = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
            if (charsetName != null) {
                try {
                    return toInputStream(text.getBytes(charsetName));
                } catch (UnsupportedEncodingException e) {
                    LOG.warn("Can't convert the String into the bytes with the charset " + charsetName, e);
                }
            }
        }
        return toInputStream(text.getBytes());
    }
    
    @Converter
    public static InputStream toInputStream(BufferedReader buffer, Exchange exchange) throws IOException {
        return toInputStream(toString(buffer), exchange);
    }

    @Converter
    public static InputStream toInputStrean(DOMSource source) throws TransformerException, IOException {
        XmlConverter xmlConverter = createXmlConverter();
        ByteArrayInputStream bais = new ByteArrayInputStream(xmlConverter.toString(source).getBytes());
        return bais;
    }

    private static XmlConverter createXmlConverter() {
        if (xmlConverter == null) {
            xmlConverter = new XmlConverter();
        }
        return xmlConverter;
    }

    @Converter
    public static String toString(byte[] data, Exchange exchange) {
        if (exchange != null) {
            String charsetName = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
            if (charsetName != null) {
                try {
                    return new String(data, charsetName);
                } catch (UnsupportedEncodingException e) {
                    LOG.warn("Can't convert the byte to String with the charset " + charsetName, e);
                }
            }
        }
        return new String(data);
    }


    @Converter
    public static String toString(File file) throws IOException {
        return toString(toReader(file));
    }

    @Converter
    public static byte[] toByteArray(File file) throws IOException {
        return toBytes(toInputStream(file));
    }

    @Converter
    public static byte[] toByteArray(Reader reader) throws IOException {
        if (reader instanceof BufferedReader) {
            return toByteArray((BufferedReader)reader);
        } else {
            return toByteArray(new BufferedReader(reader));
        }
    }

    @Converter
    public static String toString(URL url) throws IOException {
        return toString(toInputStream(url));
    }

    @Converter
    public static String toString(Reader reader) throws IOException {
        if (reader instanceof BufferedReader) {
            return toString((BufferedReader)reader);
        } else {
            return toString(new BufferedReader(reader));
        }
    }

    @Converter
    public static String toString(BufferedReader reader) throws IOException {
        if (reader == null) {
            return null;
        }
        try {
            CollectionStringBuffer builder = new CollectionStringBuffer("\n");
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    return builder.toString();
                }
                builder.append(line);
            }
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                LOG.warn("Failed to close stream: " + e, e);
            }
        }
    }

    @Converter
    public static byte[] toByteArray(BufferedReader reader) throws IOException {
        if (reader == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(1024);
        char[] buf = new char[1024];
        try {
            int len = reader.read(buf);
            if (len != -1) {
                sb.append(buf, 0, len);
            }
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                LOG.warn("Failed to close stream: " + e, e);
            }
        }
        return sb.toString().getBytes();
    }

    @Converter
    public static String toString(InputStream in) throws IOException {
        return toString(toReader(in));
    }

    @Converter
    public static InputStream toInputStream(byte[] data) {
        return new ByteArrayInputStream(data);
    }

    @Converter
    public static ObjectOutput toObjectOutput(OutputStream stream) throws IOException {
        if (stream instanceof ObjectOutput) {
            return (ObjectOutput) stream;
        } else {
            return new ObjectOutputStream(stream);
        }
    }

    @Converter
    public static ObjectInput toObjectInput(InputStream stream) throws IOException {
        if (stream instanceof ObjectInput) {
            return (ObjectInput) stream;
        } else {
            return new ObjectInputStream(stream);
        }
    }

    @Converter
    public static byte[] toBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        copy(stream, bos);
        return bos.toByteArray();
    }

    public static void copy(InputStream stream, OutputStream os) throws IOException {
        byte[] data = new byte[4096];
        int read = stream.read(data);        
        while (read != -1) {            
            os.write(data, 0, read);
            read = stream.read(data);
        }
        os.flush();
    }
}
