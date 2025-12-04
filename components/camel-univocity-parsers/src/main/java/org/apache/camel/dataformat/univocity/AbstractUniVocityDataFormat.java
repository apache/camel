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

import static org.apache.camel.support.ExchangeHelper.getCharsetName;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import com.univocity.parsers.common.AbstractParser;
import com.univocity.parsers.common.AbstractWriter;
import com.univocity.parsers.common.CommonParserSettings;
import com.univocity.parsers.common.CommonSettings;
import com.univocity.parsers.common.CommonWriterSettings;
import com.univocity.parsers.common.Format;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.service.ServiceSupport;

/**
 * This abstract class contains all the common parts for all the uniVocity parsers.
 * <p/>
 *
 * @param <F>   uniVocity format class
 * @param <CWS> uniVocity writer settings class
 * @param <W>   uniVocity writer class
 * @param <CPS> uniVocity parser settings class
 * @param <P>   uniVocity parser class
 * @param <DF>  the data format class (for providing a fluent API)
 */
public abstract class AbstractUniVocityDataFormat<
                F extends Format,
                CWS extends CommonWriterSettings<F>,
                W extends AbstractWriter<CWS>,
                CPS extends CommonParserSettings<F>,
                P extends AbstractParser<CPS>,
                DF extends AbstractUniVocityDataFormat<F, CWS, W, CPS, P, DF>>
        extends ServiceSupport implements DataFormat, DataFormatName {
    protected String nullValue;
    protected Boolean skipEmptyLines;
    protected Boolean ignoreTrailingWhitespaces;
    protected Boolean ignoreLeadingWhitespaces;
    protected boolean headersDisabled;
    protected String headers;
    protected Boolean headerExtractionEnabled;
    protected Integer numberOfRecordsToRead;
    protected String emptyValue;
    protected String lineSeparator;
    protected Character normalizedLineSeparator;
    protected Character comment;
    protected boolean lazyLoad;
    protected boolean asMap;

    private volatile CWS writerSettings;
    private volatile Marshaller<W> marshaller;

    private volatile CPS parserSettings;
    private volatile Unmarshaller<P> unmarshaller;
    private final HeaderRowProcessor headerRowProcessor = new HeaderRowProcessor();

    @Override
    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {
        if (writerSettings == null) {
            writerSettings = createAndConfigureWriterSettings();
        }
        if (marshaller == null) {
            marshaller = new Marshaller<>(headersAsArray(), headers == null);
        }

        try (Writer writer = new OutputStreamWriter(stream, getCharsetName(exchange))) {
            marshaller.marshal(exchange, body, createWriter(writer, writerSettings));
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        if (parserSettings == null) {
            parserSettings = createAndConfigureParserSettings();
        }
        if (unmarshaller == null) {
            unmarshaller = new Unmarshaller<>(lazyLoad, asMap);
        }

        P parser = createParser(parserSettings);
        // univocity-parsers is responsible for closing the reader, even in case of error
        Reader reader = new InputStreamReader(stream, getCharsetName(exchange));
        return unmarshaller.unmarshal(reader, parser, headerRowProcessor);
    }

    String[] headersAsArray() {
        if (headers != null) {
            return headers.split(",");
        } else {
            return null;
        }
    }

    /**
     * Creates a new instance of the writer settings.
     *
     * @return New instance of the writer settings
     */
    protected abstract CWS createWriterSettings();

    /**
     * Configures the writer settings.
     *
     * @param settings Writer settings to configure
     */
    protected void configureWriterSettings(CWS settings) {
        configureCommonSettings(settings);

        if (emptyValue != null) {
            settings.setEmptyValue(emptyValue);
        }
    }

    /**
     * Creates a new instance of the uniVocity writer.
     *
     * @param  writer   Output writer to use
     * @param  settings Writer settings to use
     * @return          New uinstance of the uniVocity writer
     */
    protected abstract W createWriter(Writer writer, CWS settings);

    /**
     * Creates a new instance of the parser settings.
     *
     * @return New instance of the parser settings
     */
    protected abstract CPS createParserSettings();

    /**
     * Configure the parser settings.
     *
     * @param settings Parser settings to configure
     */
    protected void configureParserSettings(CPS settings) {
        configureCommonSettings(settings);

        if (headerExtractionEnabled != null) {
            settings.setHeaderExtractionEnabled(headerExtractionEnabled);
        }
        if (numberOfRecordsToRead != null) {
            settings.setNumberOfRecordsToRead(numberOfRecordsToRead);
        }
    }

    /**
     * Creates a new instance of the uniVocity parser.
     *
     * @param  settings Parser settings to use
     * @return          New instance of the uniVocity parser
     */
    protected abstract P createParser(CPS settings);

    /**
     * Configures the format.
     *
     * @param format format to configure
     */
    protected void configureFormat(F format) {
        if (lineSeparator != null) {
            format.setLineSeparator(lineSeparator);
        }
        if (normalizedLineSeparator != null) {
            format.setNormalizedNewline(normalizedLineSeparator);
        }
        if (comment != null) {
            format.setComment(comment);
        }
    }

    /**
     * Creates and configures the writer settings.
     *
     * @return new configured instance of the writer settings
     */
    final CWS createAndConfigureWriterSettings() {
        CWS settings = createWriterSettings();
        configureWriterSettings(settings);
        configureFormat(settings.getFormat());
        return settings;
    }

    /**
     * Creates and configures the parser settings.
     *
     * @return new configured instance of the parser settings
     */
    final CPS createAndConfigureParserSettings() {
        CPS settings = createParserSettings();
        configureParserSettings(settings);
        configureFormat(settings.getFormat());
        settings.setProcessor(headerRowProcessor);
        return settings;
    }

    /**
     * Configures the common settings shared by parser and writer.
     *
     * @param settings settings to configure
     */
    private void configureCommonSettings(CommonSettings<F> settings) {
        if (nullValue != null) {
            settings.setNullValue(nullValue);
        }
        if (skipEmptyLines != null) {
            settings.setSkipEmptyLines(skipEmptyLines);
        }
        if (ignoreTrailingWhitespaces != null) {
            settings.setIgnoreTrailingWhitespaces(ignoreTrailingWhitespaces);
        }
        if (ignoreLeadingWhitespaces != null) {
            settings.setIgnoreLeadingWhitespaces(ignoreLeadingWhitespaces);
        }
        if (headersDisabled) {
            settings.setHeaders((String[]) null);
        } else if (headers != null) {
            settings.setHeaders(headersAsArray());
        }
    }

    public String getNullValue() {
        return nullValue;
    }

    public void setNullValue(String nullValue) {
        this.nullValue = nullValue;
    }

    public Boolean getSkipEmptyLines() {
        return skipEmptyLines;
    }

    public void setSkipEmptyLines(Boolean skipEmptyLines) {
        this.skipEmptyLines = skipEmptyLines;
    }

    public Boolean getIgnoreTrailingWhitespaces() {
        return ignoreTrailingWhitespaces;
    }

    public void setIgnoreTrailingWhitespaces(Boolean ignoreTrailingWhitespaces) {
        this.ignoreTrailingWhitespaces = ignoreTrailingWhitespaces;
    }

    public Boolean getIgnoreLeadingWhitespaces() {
        return ignoreLeadingWhitespaces;
    }

    public void setIgnoreLeadingWhitespaces(Boolean ignoreLeadingWhitespaces) {
        this.ignoreLeadingWhitespaces = ignoreLeadingWhitespaces;
    }

    public boolean isHeadersDisabled() {
        return headersDisabled;
    }

    public void setHeadersDisabled(boolean headersDisabled) {
        this.headersDisabled = headersDisabled;
    }

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    public Boolean getHeaderExtractionEnabled() {
        return headerExtractionEnabled;
    }

    public void setHeaderExtractionEnabled(Boolean headerExtractionEnabled) {
        this.headerExtractionEnabled = headerExtractionEnabled;
    }

    public Integer getNumberOfRecordsToRead() {
        return numberOfRecordsToRead;
    }

    public void setNumberOfRecordsToRead(Integer numberOfRecordsToRead) {
        this.numberOfRecordsToRead = numberOfRecordsToRead;
    }

    public String getEmptyValue() {
        return emptyValue;
    }

    public void setEmptyValue(String emptyValue) {
        this.emptyValue = emptyValue;
    }

    public String getLineSeparator() {
        return lineSeparator;
    }

    public void setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    public Character getNormalizedLineSeparator() {
        return normalizedLineSeparator;
    }

    public void setNormalizedLineSeparator(Character normalizedLineSeparator) {
        this.normalizedLineSeparator = normalizedLineSeparator;
    }

    public Character getComment() {
        return comment;
    }

    public void setComment(Character comment) {
        this.comment = comment;
    }

    public boolean isLazyLoad() {
        return lazyLoad;
    }

    public void setLazyLoad(boolean lazyLoad) {
        this.lazyLoad = lazyLoad;
    }

    public boolean isAsMap() {
        return asMap;
    }

    public void setAsMap(boolean asMap) {
        this.asMap = asMap;
    }

    public CWS getWriterSettings() {
        return writerSettings;
    }

    public void setWriterSettings(CWS writerSettings) {
        this.writerSettings = writerSettings;
    }

    public Marshaller<W> getMarshaller() {
        return marshaller;
    }

    public void setMarshaller(Marshaller<W> marshaller) {
        this.marshaller = marshaller;
    }

    public CPS getParserSettings() {
        return parserSettings;
    }

    public void setParserSettings(CPS parserSettings) {
        this.parserSettings = parserSettings;
    }

    public Unmarshaller<P> getUnmarshaller() {
        return unmarshaller;
    }

    public void setUnmarshaller(Unmarshaller<P> unmarshaller) {
        this.unmarshaller = unmarshaller;
    }

    public HeaderRowProcessor getHeaderRowProcessor() {
        return headerRowProcessor;
    }

    @Override
    protected void doStart() throws Exception {
        writerSettings = null;
        marshaller = null;
        parserSettings = null;
        unmarshaller = null;
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
