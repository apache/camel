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
package org.apache.camel.component.flatpack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import net.sf.flatpack.DataSet;
import net.sf.flatpack.DefaultParserFactory;
import net.sf.flatpack.Parser;
import net.sf.flatpack.ParserFactory;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultPollingEndpoint;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * The flatpack component supports fixed width and delimited file parsing via the FlatPack library.
 */
@UriEndpoint(firstVersion = "1.4.0", scheme = "flatpack", title = "Flatpack", syntax = "flatpack:type:resourceUri", label = "transformation")
public class FlatpackEndpoint extends DefaultPollingEndpoint {

    private LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
    private ParserFactory parserFactory = DefaultParserFactory.getInstance();

    @UriPath @Metadata(required = false, defaultValue = "delim")
    private FlatpackType type;
    @UriPath @Metadata(required = true)
    private String resourceUri;

    @UriParam(defaultValue = "true")
    private boolean splitRows = true;
    @UriParam
    private boolean allowShortLines;
    @UriParam
    private boolean ignoreExtraColumns;

    // delimited
    @UriParam(defaultValue = ",")
    private char delimiter = ',';
    @UriParam
    private char textQualifier = '"';
    @UriParam(defaultValue = "true")
    private boolean ignoreFirstRecord = true;

    public FlatpackEndpoint() {
    }

    public FlatpackEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component);
        this.resourceUri = resourceUri;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new FlatpackProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new FlatpackConsumer(this, processor, loadBalancer);
    }

    public void processDataSet(Exchange originalExchange, DataSet dataSet, int counter) throws Exception {
        Exchange exchange = ExchangeHelper.createCorrelatedCopy(originalExchange, false);
        Message in = exchange.getIn();
        in.setBody(dataSet);
        in.setHeader("CamelFlatpackCounter", counter);
        loadBalancer.process(exchange);
    }

    public Parser createParser(Exchange exchange) throws Exception {
        Reader bodyReader = exchange.getIn().getMandatoryBody(Reader.class);
        try {
            if (FlatpackType.fixed == type) {
                return createFixedParser(resourceUri, bodyReader);
            } else {
                return createDelimitedParser(exchange);
            }
        } catch (Exception e) {
            // must close reader in case of some exception
            IOHelper.close(bodyReader);
            throw e;
        }
    }

    protected Parser createFixedParser(String resourceUri, Reader bodyReader) throws IOException {
        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), resourceUri);
        InputStreamReader reader = new InputStreamReader(is);
        Parser parser = getParserFactory().newFixedLengthParser(reader, bodyReader);
        if (isAllowShortLines()) {
            parser.setHandlingShortLines(true);
            parser.setIgnoreParseWarnings(true);
        }
        if (isIgnoreExtraColumns()) {
            parser.setIgnoreExtraColumns(true);
            parser.setIgnoreParseWarnings(true);
        }
        return parser;
    }

    public Parser createDelimitedParser(Exchange exchange) throws InvalidPayloadException, IOException {
        Reader bodyReader = exchange.getIn().getMandatoryBody(Reader.class);

        Parser parser;
        if (ObjectHelper.isEmpty(getResourceUri())) {
            parser = getParserFactory().newDelimitedParser(bodyReader, delimiter, textQualifier);
        } else {
            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), resourceUri);
            InputStreamReader reader = new InputStreamReader(is, ExchangeHelper.getCharsetName(exchange));
            parser = getParserFactory().newDelimitedParser(reader, bodyReader, delimiter, textQualifier, ignoreFirstRecord);
        }

        if (isAllowShortLines()) {
            parser.setHandlingShortLines(true);
            parser.setIgnoreParseWarnings(true);
        }
        if (isIgnoreExtraColumns()) {
            parser.setIgnoreExtraColumns(true);
            parser.setIgnoreParseWarnings(true);
        }

        return parser;
    }


    // Properties
    //-------------------------------------------------------------------------

    public String getResourceUri() {
        return resourceUri;
    }

    public ParserFactory getParserFactory() {
        return parserFactory;
    }

    public void setParserFactory(ParserFactory parserFactory) {
        this.parserFactory = parserFactory;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public boolean isSplitRows() {
        return splitRows;
    }

    /**
     * Sets the Component to send each row as a separate exchange once parsed
     */
    public void setSplitRows(boolean splitRows) {
        this.splitRows = splitRows;
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

    public FlatpackType getType() {
        return type;
    }

    /**
     * Whether to use fixed or delimiter
     */
    public void setType(FlatpackType type) {
        this.type = type;
    }

    /**
     * URL for loading the flatpack mapping file from classpath or file system
     */
    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public char getDelimiter() {
        return delimiter;
    }

    /**
     * The default character delimiter for delimited files.
     */
    public void setDelimiter(char delimiter) {
        this.delimiter = delimiter;
    }

    public char getTextQualifier() {
        return textQualifier;
    }

    /**
     * The text qualifier for delimited files.
     */
    public void setTextQualifier(char textQualifier) {
        this.textQualifier = textQualifier;
    }

    public boolean isIgnoreFirstRecord() {
        return ignoreFirstRecord;
    }

    /**
     * Whether the first line is ignored for delimited files (for the column headers).
     */
    public void setIgnoreFirstRecord(boolean ignoreFirstRecord) {
        this.ignoreFirstRecord = ignoreFirstRecord;
    }
}
