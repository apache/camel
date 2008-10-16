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
package org.apache.camel.component.flatpack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.List;
import java.util.Map;

import net.sf.flatpack.DataSet;
import net.sf.flatpack.DefaultParserFactory;
import net.sf.flatpack.Parser;
import net.sf.flatpack.ParserFactory;
import net.sf.flatpack.writer.DelimiterWriterFactory;
import net.sf.flatpack.writer.FixedWriterFactory;
import net.sf.flatpack.writer.Writer;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.JDOMException;
import org.springframework.core.io.Resource;

/**
 * Flatpack DataFormat.
 * <p/>
 * This data format supports two operations:
 * <ul>
 * <li>marshal = from <tt>List&lt;Map&lt;String, Object&gt;&gt;</tt> to <tt>OutputStream</tt> (can be converted to String)</li>
 * <li>unmarshal = from <tt>InputStream</tt> (such as a File) to {@link DataSetList}.
 * </ul>
 * <b>Notice:</b> The Flatpack library does currently not support header and trailers for the marshal operation.
 *
 * @version $Revision$
 */
public class FlatpackDataFormat implements DataFormat {
    private static final transient Log LOG = LogFactory.getLog(FlatpackDataFormat.class);
    private ParserFactory parserFactory = DefaultParserFactory.getInstance();
    private char delimiter = ',';
    private char textQualifier = '"';
    private boolean ignoreFirstRecord = true;
    private Resource definition;
    private boolean fixed;

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
                for (String key : row.keySet()) {
                    writer.addRecordEntry(key, row.get(key));
                }
                writer.nextRecord();
            }
            writer.printFooter();
        } finally {
            writer.flush();
            writer.close();
        }
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        InputStreamReader reader = new InputStreamReader(stream);
        try {
            Parser parser = createParser(exchange, reader);
            DataSet dataSet = parser.parse();
            return new DataSetList(dataSet);
        } finally {
            reader.close();
        }
    }

    // Properties
    //-------------------------------------------------------------------------

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

    public Resource getDefinition() {
        return definition;
    }

    public void setDefinition(Resource definition) {
        this.definition = definition;
    }

    public ParserFactory getParserFactory() {
        return parserFactory;
    }

    public void setParserFactory(ParserFactory parserFactory) {
        this.parserFactory = parserFactory;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected Parser createParser(Exchange exchange, Reader bodyReader) throws IOException {
        if (isFixed()) {
            Resource resource = getDefinition();
            ObjectHelper.notNull(resource, "resource property");
            return getParserFactory().newFixedLengthParser(new InputStreamReader(resource.getInputStream()), bodyReader);
        } else {
            Resource resource = getDefinition();
            if (resource == null) {
                return getParserFactory().newDelimitedParser(bodyReader, delimiter, textQualifier);
            } else {
                return getParserFactory().newDelimitedParser(new InputStreamReader(resource.getInputStream()), bodyReader, delimiter, textQualifier, ignoreFirstRecord);
            }
        }
    }

    private Writer createWriter(Exchange exchange, Map<String, Object> firstRow, OutputStream stream) throws JDOMException, IOException {
        if (isFixed()) {
            Resource resource = getDefinition();
            ObjectHelper.notNull(resource, "resource property");
            FixedWriterFactory factory = new FixedWriterFactory(new InputStreamReader(resource.getInputStream()));
            return factory.createWriter(new OutputStreamWriter(stream));
        } else {
            Resource resource = getDefinition();
            if (resource == null) {
                DelimiterWriterFactory factory = new DelimiterWriterFactory(delimiter, textQualifier);
                // add coulmns from the keys in the data map as the columns must be known
                for (String key : firstRow.keySet()) {
                    factory.addColumnTitle(key);
                }
                return factory.createWriter(new OutputStreamWriter(stream));
            } else {
                DelimiterWriterFactory factory = new DelimiterWriterFactory(new InputStreamReader(resource.getInputStream()), delimiter, textQualifier);
                return factory.createWriter(new OutputStreamWriter(stream));
            }
        }
    }


}
