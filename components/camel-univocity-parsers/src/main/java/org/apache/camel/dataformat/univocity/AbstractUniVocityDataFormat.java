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

import static org.apache.camel.support.ExchangeHelper.getCharsetName;

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
public abstract class AbstractUniVocityDataFormat<F extends Format, CWS extends CommonWriterSettings<F>,
        W extends AbstractWriter<CWS>, CPS extends CommonParserSettings<F>, P extends AbstractParser<CPS>, DF extends AbstractUniVocityDataFormat<F, CWS, W, CPS, P, DF>>
        extends ServiceSupport implements DataFormat, DataFormatName {
    protected String nullValue;
    protected Boolean skipEmptyLines;
    protected Boolean ignoreTrailingWhitespaces;
    protected Boolean ignoreLeadingWhitespaces;
    protected boolean headersDisabled;
    protected String[] headers;
    protected Boolean headerExtractionEnabled;
    protected Integer numberOfRecordsToRead;
    protected String emptyValue;
    protected String lineSeparator;
    protected Character normalizedLineSeparator;
    protected Character comment;
    protected boolean lazyLoad;
    protected boolean asMap;

    private volatile CWS writerSettings;
    private final Object writerSettingsToken = new Object();
    private volatile Marshaller<W> marshaller;

    // We're using a ThreadLocal for the parser settings because in order to retrieve the headers we need to change the
    // settings each time we're parsing
    private volatile ThreadLocal<CPS> parserSettings;
    private final Object parserSettingsToken = new Object();
    private volatile Unmarshaller<P> unmarshaller;

    /**
     * {@inheritDoc}
     */
    @Override
    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {
        if (writerSettings == null) {
            synchronized (writerSettingsToken) {
                if (writerSettings == null) {
                    marshaller = new Marshaller<>(headers, headers == null);
                    writerSettings = createAndConfigureWriterSettings();
                }
            }
        }

        Writer writer = new OutputStreamWriter(stream, getCharsetName(exchange));
        try {
            marshaller.marshal(exchange, body, createWriter(writer, writerSettings));
        } finally {
            writer.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        if (parserSettings == null) {
            synchronized (parserSettingsToken) {
                if (parserSettings == null) {
                    unmarshaller = new Unmarshaller<>(lazyLoad, asMap);
                    parserSettings = new ThreadLocal<CPS>() {
                        @Override
                        protected CPS initialValue() {
                            return createAndConfigureParserSettings();
                        }
                    };
                }
            }
        }

        HeaderRowProcessor headerRowProcessor = new HeaderRowProcessor();
        CPS settings = parserSettings.get();
        settings.setProcessor(headerRowProcessor);
        P parser = createParser(settings);
        // univocity-parsers is responsible for closing the reader, even in case of error
        Reader reader = new InputStreamReader(stream, getCharsetName(exchange));
        return unmarshaller.unmarshal(reader, parser, headerRowProcessor);
    }

    /**
     * Gets the String representation of a null value.
     * If {@code null} then the default settings value is used.
     *
     * @return the String representation of a null value
     * @see com.univocity.parsers.common.CommonSettings#getNullValue()
     */
    public String getNullValue() {
        return nullValue;
    }

    /**
     * Sets the String representation of a null value.
     * If {@code null} then the default settings value is used.
     *
     * @param nullValue the String representation of a null value
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.common.CommonSettings#setNullValue(String)
     */
    public DF setNullValue(String nullValue) {
        this.nullValue = nullValue;
        return self();
    }

    /**
     * Gets whether or not empty lines should be ignored.
     * If {@code null} then the default settings value is used.
     *
     * @return whether or not empty lines should be ignored
     * @see com.univocity.parsers.common.CommonSettings#getSkipEmptyLines()
     */
    public Boolean getSkipEmptyLines() {
        return skipEmptyLines;
    }

    /**
     * Sets whether or not empty lines should be ignored.
     * If {@code null} then the default settings value is used.
     *
     * @param skipEmptyLines whether or not empty lines should be ignored
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.common.CommonSettings#setSkipEmptyLines(boolean)
     */
    public DF setSkipEmptyLines(Boolean skipEmptyLines) {
        this.skipEmptyLines = skipEmptyLines;
        return self();
    }

    /**
     * Gets whether or not trailing whitespaces should be ignored.
     * If {@code null} then the default settings value is used.
     *
     * @return whether or not trailing whitespaces should be ignored
     * @see com.univocity.parsers.common.CommonSettings#getIgnoreTrailingWhitespaces()
     */
    public Boolean getIgnoreTrailingWhitespaces() {
        return ignoreTrailingWhitespaces;
    }

    /**
     * Sets whether or not trailing whitespaces should be ignored.
     * If {@code null} then the default settings value is used.
     *
     * @param ignoreTrailingWhitespaces whether or not trailing whitespaces should be ignored
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.common.CommonSettings#setIgnoreTrailingWhitespaces(boolean)
     */
    public DF setIgnoreTrailingWhitespaces(Boolean ignoreTrailingWhitespaces) {
        this.ignoreTrailingWhitespaces = ignoreTrailingWhitespaces;
        return self();
    }

    /**
     * Gets whether or not leading whitespaces should be ignored.
     * If {@code null} then the default settings value is used.
     *
     * @return whether or not leading whitespaces should be ignored
     * @see com.univocity.parsers.common.CommonSettings#getIgnoreLeadingWhitespaces()
     */
    public Boolean getIgnoreLeadingWhitespaces() {
        return ignoreLeadingWhitespaces;
    }

    /**
     * Sets whether or not leading whitespaces should be ignored.
     * If {@code null} then the default settings value is used.
     *
     * @param ignoreLeadingWhitespaces whether or not leading whitespaces should be ignored
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.common.CommonSettings#setIgnoreLeadingWhitespaces(boolean)
     */
    public DF setIgnoreLeadingWhitespaces(Boolean ignoreLeadingWhitespaces) {
        this.ignoreLeadingWhitespaces = ignoreLeadingWhitespaces;
        return self();
    }

    /**
     * Gets whether or not headers are disabled.
     * If {@code true} then it passes {@code null} to
     * {@link com.univocity.parsers.common.CommonSettings#setHeaders(String...)} in order to disabled them.
     *
     * @return whether or not headers are disabled
     * @see com.univocity.parsers.common.CommonSettings#getHeaders()
     */
    public boolean isHeadersDisabled() {
        return headersDisabled;
    }

    /**
     * Sets whether or not headers are disabled.
     * If {@code true} then it passes {@code null} to
     * {@link com.univocity.parsers.common.CommonSettings#setHeaders(String...)} in order to disabled them.
     *
     * @param headersDisabled whether or not headers are disabled
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.common.CommonSettings#setHeaders(String...)
     */
    public DF setHeadersDisabled(boolean headersDisabled) {
        this.headersDisabled = headersDisabled;
        return self();
    }

    /**
     * Gets the headers.
     * If {@code null} then the default settings value is used.
     *
     * @return the headers
     * @see com.univocity.parsers.common.CommonSettings#getHeaders()
     */
    public String[] getHeaders() {
        return headers;
    }

    /**
     * Sets the headers.
     * If {@code null} then the default settings value is used.
     *
     * @param headers the headers
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.common.CommonSettings#setHeaders(String...)
     */
    public DF setHeaders(String[] headers) {
        this.headers = headers;
        return self();
    }

    /**
     * Gets whether or not the header extraction is enabled.
     * If {@code null} then the default settings value is used.
     *
     * @return whether or not the header extraction is enabled
     * @see com.univocity.parsers.common.CommonParserSettings#isHeaderExtractionEnabled()
     */
    public Boolean getHeaderExtractionEnabled() {
        return headerExtractionEnabled;
    }

    /**
     * Sets whether or not the header extraction is enabled.
     * If {@code null} then the default settings value is used.
     *
     * @param headerExtractionEnabled whether or not the header extraction is enabled
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.common.CommonParserSettings#setHeaderExtractionEnabled(boolean)
     */
    public DF setHeaderExtractionEnabled(Boolean headerExtractionEnabled) {
        this.headerExtractionEnabled = headerExtractionEnabled;
        return self();
    }

    /**
     * Gets the number of records to read.
     * If {@code null} then the default settings value is used.
     *
     * @return the number of records to read
     * @see com.univocity.parsers.common.CommonParserSettings#getNumberOfRecordsToRead()
     */
    public Integer getNumberOfRecordsToRead() {
        return numberOfRecordsToRead;
    }

    /**
     * Sets the number of records to read.
     * If {@code null} then the default settings value is used.
     *
     * @param numberOfRecordsToRead the number of records to read
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.common.CommonParserSettings#setNumberOfRecordsToRead(long)
     */
    public DF setNumberOfRecordsToRead(Integer numberOfRecordsToRead) {
        this.numberOfRecordsToRead = numberOfRecordsToRead;
        return self();
    }

    /**
     * Gets the String representation of an empty value.
     * If {@code null} then the default settings value is used.
     *
     * @return the String representation of an empty value
     * @see com.univocity.parsers.common.CommonWriterSettings#getEmptyValue()
     */
    public String getEmptyValue() {
        return emptyValue;
    }

    /**
     * Sets the String representation of an empty value.
     * If {@code null} then the default settings value is used.
     *
     * @param emptyValue the String representation of an empty value
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.common.CommonWriterSettings#setEmptyValue(String)
     */
    public DF setEmptyValue(String emptyValue) {
        this.emptyValue = emptyValue;
        return self();
    }

    /**
     * Gets the line separator.
     * If {@code null} then the default format value is used.
     *
     * @return the line separator
     * @see com.univocity.parsers.common.Format#getLineSeparatorString()
     */
    public String getLineSeparator() {
        return lineSeparator;
    }

    /**
     * Sets the line separator.
     * If {@code null} then the default format value is used.
     *
     * @param lineSeparator the line separator
     * @return current data format instance, fluent API
     * @see Format#setLineSeparator(String)
     */
    public DF setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
        return self();
    }

    /**
     * Gets the normalized line separator.
     * If {@code null} then the default format value is used.
     *
     * @return the normalized line separator
     * @see com.univocity.parsers.common.Format#getNormalizedNewline()
     */
    public Character getNormalizedLineSeparator() {
        return normalizedLineSeparator;
    }

    /**
     * Sets the normalized line separator.
     * If {@code null} then the default format value is used.
     *
     * @param normalizedLineSeparator the normalized line separator
     * @return current data format instance, fluent API
     * @see Format#setNormalizedNewline(char)
     */
    public DF setNormalizedLineSeparator(Character normalizedLineSeparator) {
        this.normalizedLineSeparator = normalizedLineSeparator;
        return self();
    }

    /**
     * Gets the comment symbol.
     * If {@code null} then the default format value is used.
     *
     * @return the comment symbol
     * @see com.univocity.parsers.common.Format#getComment()
     */
    public Character getComment() {
        return comment;
    }

    /**
     * Gets the comment symbol.
     * If {@code null} then the default format value is used.
     *
     * @param comment the comment symbol
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.common.Format#setComment(char)
     */
    public DF setComment(Character comment) {
        this.comment = comment;
        return self();
    }

    /**
     * Gets whether or not the unmarshalling should read lines lazily.
     *
     * @return whether or not the unmarshalling should read lines lazily
     */
    public boolean isLazyLoad() {
        return lazyLoad;
    }

    /**
     * Sets whether or not the unmarshalling should read lines lazily.
     *
     * @param lazyLoad whether or not the unmarshalling should read lines lazily
     * @return current data format instance, fluent API
     */
    public DF setLazyLoad(boolean lazyLoad) {
        this.lazyLoad = lazyLoad;
        return self();
    }

    /**
     * Gets whether or not the unmarshalling should produces maps instead of lists.
     *
     * @return whether or not the unmarshalling should produces maps instead of lists
     */
    public boolean isAsMap() {
        return asMap;
    }

    /**
     * Sets whether or not the unmarshalling should produces maps instead of lists.
     *
     * @param asMap whether or not the unmarshalling should produces maps instead of lists
     * @return current data format instance, fluent API
     */
    public DF setAsMap(boolean asMap) {
        this.asMap = asMap;
        return self();
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
     * @param writer   Output writer to use
     * @param settings Writer settings to use
     * @return New uinstance of the uniVocity writer
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
     * @param settings Parser settings to use
     * @return New instance of the uniVocity parser
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
            settings.setHeaders(headers);
        }
    }

    /**
     * Returns {@code this} as the proper data format type. It helps the fluent API with inheritance.
     *
     * @return {@code this} as the proper data format type
     */
    @SuppressWarnings("unchecked")
    private DF self() {
        return (DF) this;
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
