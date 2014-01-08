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

package org.apache.camel.dataformat.csv;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.camel.util.IOHelper;
import org.apache.commons.csv.CSVParser;

/**
 */
public class CsvIterator<T> implements Iterator<T>, Closeable {

    private final CSVParser parser;
    private final Reader reader;
    private final CsvLineConverter<T> lineConverter;
    private String[] line;

    public CsvIterator(CSVParser parser, Reader reader, CsvLineConverter<T> lineConverter) throws IOException {
        this.parser = parser;
        this.reader = reader;
        this.lineConverter = lineConverter;
        line = parser.getLine();
    }

    @Override
    public boolean hasNext() {
        return line != null;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        T result = lineConverter.convertLine(line);
        try {
            line = parser.getLine();
        } catch (IOException e) {
            line = null;
            close();
            throw new IllegalStateException(e);
        }
        if (line == null) {
            close();
        }
        return result;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        IOHelper.close(reader);
    }
}
