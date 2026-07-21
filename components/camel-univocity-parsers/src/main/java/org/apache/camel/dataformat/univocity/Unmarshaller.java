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
package org.apache.camel.dataformat.univocity;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.univocity.parsers.common.AbstractParser;

/**
 * This class unmarshalls the exchange body using an uniVocity parser.
 *
 * @param <P> Parser class
 */
final class Unmarshaller<P extends AbstractParser<?>> {
    private final boolean lazyLoad;
    private final boolean asMap;

    /**
     * Creates a new instance.
     *
     * @param lazyLoad whether or not the lines must be lazily read
     * @param asMap    whether or not we must produce maps instead of lists for each row
     */
    Unmarshaller(boolean lazyLoad, boolean asMap) {
        this.lazyLoad = lazyLoad;
        this.asMap = asMap;
    }

    /**
     * Unmarshal from the given reader.
     *
     * @param  reader             reader to read from
     * @param  parser             uniVocity parser to use
     * @param  headerRowProcessor Row processor that retrieves the header
     * @return                    Unmarshalled data
     */
    public Object unmarshal(Reader reader, P parser, HeaderRowProcessor headerRowProcessor) {
        parser.beginParsing(reader);
        Iterator<?> iterator = asMap ? new MapRowIterator<>(parser, headerRowProcessor) : new ListRowIterator<>(parser);
        return lazyLoad ? iterator : convertToList(iterator);
    }

    /**
     * Converts the given iterator into a list.
     *
     * @param  iterator iterator to convert
     * @param  <T>      item class
     * @return          a list that contains all the items of the iterator
     */
    private static <T> List<T> convertToList(Iterator<T> iterator) {
        List<T> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    /**
     * This abstract class helps iterating over the rows using uniVocity.
     *
     * @param <E> Row class
     * @param <P> Parser class
     */
    private abstract static class RowIterator<E, P extends AbstractParser<?>> implements Iterator<E>, Closeable {
        private final P parser;
        private String[] row;

        protected RowIterator(P parser) {
            this.parser = parser;
            row = this.parser.parseNext();
        }

        @Override
        public final boolean hasNext() {
            return row != null;
        }

        @Override
        public final E next() {
            if (row == null) {
                throw new NoSuchElementException();
            }

            E result = convertRow(row);
            row = parser.parseNext();
            return result;
        }

        @Override
        public final void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() throws IOException {
            parser.stopParsing();
        }

        protected abstract E convertRow(String[] row);
    }

    /**
     * This class is an iterator that transforms each row into a List.
     *
     * @param <P> Parser class
     */
    private static final class ListRowIterator<P extends AbstractParser<?>> extends RowIterator<List<String>, P> {

        protected ListRowIterator(P parser) {
            super(parser);
        }

        @Override
        protected List<String> convertRow(String[] row) {
            return Arrays.asList(row);
        }
    }

    /**
     * This class is an iterator that transform each row into a Map.
     *
     * @param <P> Parser class
     */
    private static class MapRowIterator<P extends AbstractParser<?>> extends RowIterator<Map<String, String>, P> {
        // Captured eagerly on the parsing thread to avoid ThreadLocal cross-thread issues
        private final String[] headers;

        protected MapRowIterator(P parser, HeaderRowProcessor headerRowProcessor) {
            super(parser);
            this.headers = headerRowProcessor.getHeaders();
        }

        @Override
        protected Map<String, String> convertRow(String[] row) {
            int size = Math.min(row.length, headers.length);
            Map<String, String> result = new LinkedHashMap<>(size);
            for (int i = 0; i < size; i++) {
                result.put(headers[i], row[i]);
            }
            return result;
        }
    }
}
