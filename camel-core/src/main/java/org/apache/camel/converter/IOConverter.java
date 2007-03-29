/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.converter;

import org.apache.camel.Converter;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * Some core java.io based converters
 *
 * @version $Revision$
 */
@Converter
public class IOConverter {

    @Converter
    public static InputStream toInputStream(File file) throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    @Converter
    public static BufferedReader toReader(File file) throws FileNotFoundException {
        return new BufferedReader(new FileReader(file));
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
    public static StringReader toInputStream(String text) {
        // TODO could we automatically find this?
        return new StringReader(text);
    }

    @Converter
    public static byte[] toByteArray(String text) {
        // TODO could we automatically find this?
        return text.getBytes();
    }

    @Converter
    public static String toString(byte[] data) {
        return new String(data);
    }

    @Converter
    public static InputStream toInputStream(byte[] data) {
        return new ByteArrayInputStream(data);
    }
}
