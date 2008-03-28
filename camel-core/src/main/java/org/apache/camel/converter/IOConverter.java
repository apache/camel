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
import java.io.Writer;
import java.net.URL;
import java.util.Properties;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.camel.Converter;
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
        // TODO could we automatically find this?
        return new StringReader(text);
    }

    @Converter
    public static InputStream toInputStream(String text) {
        return toInputStream(text.getBytes());
    }

    @Converter
    public static InputStream toInputStream(BufferedReader buffer) throws IOException {
        return toInputStream(toString(buffer));
    }

    @Converter
    public static InputStream toInputStrean(DOMSource source) throws TransformerException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(toString(source).getBytes());
        return bais;
    }

    @Converter
    public static String toString(byte[] data) {
        return new String(data);
    }

    @Converter
    public static String toString(File file) throws IOException {
        return toString(toReader(file));
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
    public static String toString(InputStream in) throws IOException {
        return toString(toReader(in));
    }

    public static String toString(Source source) throws TransformerException, IOException {
        return toString(source, null);
    }

    public static String toString(Source source, Properties props) throws TransformerException, IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        StreamResult sr = new StreamResult(bos);
        Transformer trans = TransformerFactory.newInstance().newTransformer();
        if (props == null) {
            props = new Properties();
            props.put(OutputKeys.OMIT_XML_DECLARATION, "yes");
        }
        trans.setOutputProperties(props);
        trans.transform(source, sr);
        bos.close();
        return bos.toString();
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

    protected static void copy(InputStream stream, ByteArrayOutputStream bos) throws IOException {
        byte[] data = new byte[4096];
        int read = stream.read(data);
        while (read != -1) {
            bos.write(data, 0, read);
            read = stream.read(data);
        }
        bos.flush();
    }
}
