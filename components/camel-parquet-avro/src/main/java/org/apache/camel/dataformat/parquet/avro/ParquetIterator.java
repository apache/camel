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
package org.apache.camel.dataformat.parquet.avro;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.camel.RuntimeCamelException;
import org.apache.parquet.hadoop.ParquetReader;

public class ParquetIterator<T> implements Iterator<T>, Closeable {
    private final ParquetReader<T> reader;
    private T current;

    public ParquetIterator(ParquetReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public boolean hasNext() {
        if (current == null) {
            current = getNext();
        }
        return current != null;
    }

    @Override
    public T next() {
        T next = current;
        current = null;
        if (next == null) {
            next = getNext();
            if (next == null) {
                throw new NoSuchElementException("No more items available");
            }
        }
        return next;
    }

    private T getNext() {
        try {
            return reader.read();
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
