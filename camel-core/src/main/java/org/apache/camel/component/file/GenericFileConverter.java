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
package org.apache.camel.component.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Converter;
import org.apache.camel.converter.IOConverter;

/**
 * A set of converter methods for working with remote file types
 */
@Converter
public final class GenericFileConverter {

    private GenericFileConverter() {
        // Helper Class
    }

    @Converter
    public static InputStream toInputStream(GenericFile<File> file) throws FileNotFoundException {
        return IOConverter.toInputStream(file.getFile());
    }

    @Converter
    public static BufferedReader toReader(GenericFile<File> file) throws FileNotFoundException {
        return IOConverter.toReader(file.getFile());
    }

    @Converter
    public static OutputStream toOutputStream(GenericFile<File> file) throws FileNotFoundException {
        return IOConverter.toOutputStream(file.getFile());
    }

    @Converter
    public static BufferedWriter toWriter(GenericFile<File> file) throws IOException {
        return IOConverter.toWriter(file.getFile());
    }

    @Converter
    public static byte[] toByteArray(GenericFile<File> file) throws IOException {
        return IOConverter.toByteArray(file.getFile());
    }

    @Converter
    public static String toString(GenericFile<File> file) throws IOException {
        return IOConverter.toString(file.getFile());
    }

}
