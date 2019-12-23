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
package org.apache.camel.dataformat.flatpack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.flatpack.DataSet;
import net.sf.flatpack.DefaultParserFactory;
import net.sf.flatpack.Parser;
import net.sf.flatpack.ParserFactory;
import net.sf.flatpack.writer.DelimiterWriterFactory;
import net.sf.flatpack.writer.FixedWriterFactory;
import net.sf.flatpack.writer.Writer;
import org.apache.camel.Exchange;
import org.apache.camel.component.flatpack.DataSetList;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flatpack DataFormat.
 * <p/>
 * This data format supports two operations:
 * <ul>
 * <li>marshal = from <tt>List&lt;Map&lt;String, Object&gt;&gt;</tt> to <tt>OutputStream</tt> (can be converted to String)</li>
 * <li>unmarshal = from <tt>InputStream</tt> (such as a File) to {@link org.apache.camel.component.flatpack.DataSetList}.
 * </ul>
 * <b>Notice:</b> The Flatpack library does currently not support header and trailers for the marshal operation.
 */
@Dataformat("flatpack")
public class FlatpackDataFormat extends ServiceSupport implements DataFormat, DataFormatName {
    private static final Logger LOG = LoggerFactory.getLogger(FlatpackDataFormat.class);
    private ParserFactory parserFactory = DefaultParserFactory.getInstance();
    private char delimiter = ',';
    private char textQualifier = '"';
    private boolean ignoreFirstRecord = true;
    private boolean fixed;
    private boolean allowShortLines;
    private boolean ignoreExtraColumns;
    private String definition;

    @Override
    public String getDataFormatName() {
        return "flatback";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        ObjectHelper.notNull(graph, "The object to marshal must be provided");

        List<Map<String, Object>> data = (List<Map<String, Object>>) graph;
        if (data.isEmpty()) {
            LOG.warn("No data to marshal as the list is empty");
            return;
        }
        Map<String, Object> firstRow = data.get(0);

        Writer writer = createWriter(exchange, firstRow, stream);
        try {
            boolean first = true;
            writer.printHeader();
            for (Map<String, Object> row : data) {
                if (ignoreFirstRecord && first) {
                    // skip first row
                    first = false;
                    continue;
                }
                for (Entry<String, Object> entry : row.entrySet()) {
                    writer.addRecordEntry(entry.getKey(), entry.getValue());
                }
                writer.nextRecord();
            }
            writer.printFooter();
        } finally {
            writer.flush();
            writer.close();
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        InputStreamReader reader = new InputStreamReader(stream, ExchangeHelper.getCharsetName(exchange));
        try {
            Parser parser = createParser(exchange, reader);
            DataSet dataSet = parser.parse();
            return new DataSetList(dataSet);
        } finally {
            reader.close();
        }
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop

    }

    // Properties
    //-------------------------------------------------------------------------

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public boolean isFixed() {
        return fixed;
    }

    public void setFixed(boolean fixed) {
        this.fixed = fixed;
    }

    public char getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(char delimiter) {
        this.delimiter = delimiter;
    }

    public boolean isIgnoreFirstRecord() {
        return ignoreFirstRecord;
    }

    public void setIgnoreFirstRecord(boolean ignoreFirstRecord) {
        this.ignoreFirstRecord = ignoreFirstRecord;
    }

    public char getTextQualifier() {
        return textQualifier;
    }

    public void setTextQualifier(char textQualifier) {
        this.textQualifier = textQualifier;
    }

    public ParserFactory getParserFactory() {
        return parserFactory;
    }

    public void setParserFactory(ParserFactory parserFactory) {
        this.parserFactory = parserFactory;
    }

    public boolean isAllowShortLines() {
        return this.allowShortLines;
    }

    /**
     * Allows for lines to be shorter than expected and ignores the extra characters
     */
    public void setAllowShortLines(boolean allowShortLines) {
        this.allowShortLines = allowShortLines;
    }

    /**
     * Allows for lines to be longer than expected and ignores the extra characters
     */
    public void setIgnoreExtraColumns(boolean ignoreExtraColumns) {
        this.ignoreExtraColumns = ignoreExtraColumns;
    }

    public boolean isIgnoreExtraColumns() {
        return ignoreExtraColumns;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected Parser createParser(Exchange exchange, Reader bodyReader) throws IOException {
        if (isFixed()) {
            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(exchange.getContext(), getDefinition());
            InputStreamReader reader = new InputStreamReader(is, ExchangeHelper.getCharsetName(exchange));
            Parser parser = getParserFactory().newFixedLengthParser(reader, bodyReader);
            if (allowShortLines) {
                parser.setHandlingShortLines(true);
                parser.setIgnoreParseWarnings(true);
            }
            if (ignoreExtraColumns) {
                parser.setIgnoreExtraColumns(true);
                parser.setIgnoreParseWarnings(true);
            }
            return parser;
        } else {
            if (ObjectHelper.isEmpty(getDefinition())) {
                return getParserFactory().newDelimitedParser(bodyReader, delimiter, textQualifier);
            } else {
                InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(exchange.getContext(), getDefinition());
                InputStreamReader reader = new InputStreamReader(is, ExchangeHelper.getCharsetName(exchange));
                Parser parser = getParserFactory().newDelimitedParser(reader, bodyReader, delimiter, textQualifier, ignoreFirstRecord);
                if (allowShortLines) {
                    parser.setHandlingShortLines(true);
                    parser.setIgnoreParseWarnings(true);
                }
                if (ignoreExtraColumns) {
                    parser.setIgnoreExtraColumns(true);
                    parser.setIgnoreParseWarnings(true);
                }
                return parser;
            }
        }
    }

    private Writer createWriter(Exchange exchange, Map<String, Object> firstRow, OutputStream stream) throws IOException {
        if (isFixed()) {
            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(exchange.getContext(), getDefinition());
            InputStreamReader reader = new InputStreamReader(is, ExchangeHelper.getCharsetName(exchange));
            FixedWriterFactory factory = new FixedWriterFactory(reader);
            return factory.createWriter(new OutputStreamWriter(stream, ExchangeHelper.getCharsetName(exchange)));
        } else {
            if (getDefinition() == null) {
                DelimiterWriterFactory factory = new DelimiterWriterFactory(delimiter, textQualifier);
                // add columns from the keys in the data map as the columns must be known
                for (String key : firstRow.keySet()) {
                    factory.addColumnTitle(key);
                }
                return factory.createWriter(new OutputStreamWriter(stream, ExchangeHelper.getCharsetName(exchange)));
            } else {
                InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(exchange.getContext(), getDefinition());
                InputStreamReader reader = new InputStreamReader(is, ExchangeHelper.getCharsetName(exchange));
                DelimiterWriterFactory factory = new DelimiterWriterFactory(reader, delimiter, textQualifier);
                return factory.createWriter(new OutputStreamWriter(stream, ExchangeHelper.getCharsetName(exchange)));
            }
        }
    }

}
