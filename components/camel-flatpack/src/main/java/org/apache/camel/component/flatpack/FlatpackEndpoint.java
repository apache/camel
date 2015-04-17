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
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;

/**
 * Processing fixed width or delimited files or messages using the FlatPack library.
 *
 * @version 
 */
@UriEndpoint(scheme = "flatpack", title = "Flatpack", syntax = "flatpack:type:resourceUri", consumerClass = FlatpackConsumer.class, label = "transformation")
public class FlatpackEndpoint extends DefaultPollingEndpoint {

    private LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
    private ParserFactory parserFactory = DefaultParserFactory.getInstance();
   
    @UriPath @Metadata(required = "true")
    private FlatpackType type;
    @UriPath @Metadata(required = "true")
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

    public boolean isSingleton() {
        return true;
    }

    public Producer createProducer() throws Exception {
        return new FlatpackProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new FlatpackConsumer(this, processor, loadBalancer);
    }

    public void processDataSet(Exchange originalExchange, DataSet dataSet, int counter) throws Exception {
        Exchange exchange = originalExchange.copy();
        Message in = exchange.getIn();
        in.setBody(dataSet);
        in.setHeader("CamelFlatpackCounter", counter);
        loadBalancer.process(exchange);
    }

    public Parser createParser(Exchange exchange) throws InvalidPayloadException, IOException {
        Reader bodyReader = exchange.getIn().getMandatoryBody(Reader.class);
        if (FlatpackType.fixed == type) {
            return createFixedParser(resourceUri, bodyReader);
        } else {
            return createDelimitedParser(exchange);
        }
    }

    protected Parser createFixedParser(String resourceUri, Reader bodyReader) throws IOException {
        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext().getClassResolver(), resourceUri);
        InputStreamReader reader = new InputStreamReader(is);
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
    }

    public Parser createDelimitedParser(Exchange exchange) throws InvalidPayloadException, IOException {
        Reader bodyReader = exchange.getIn().getMandatoryBody(Reader.class);
        if (ObjectHelper.isEmpty(getResourceUri())) {
            return getParserFactory().newDelimitedParser(bodyReader, delimiter, textQualifier);
        } else {
            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext().getClassResolver(), resourceUri);
            InputStreamReader reader = new InputStreamReader(is, IOHelper.getCharsetName(exchange));
            Parser parser = getParserFactory().newDelimitedParser(reader, bodyReader, delimiter, textQualifier, ignoreFirstRecord);
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

    public void setType(FlatpackType type) {
        this.type = type;
    }

    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public char getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(char delimiter) {
        this.delimiter = delimiter;
    }

    public char getTextQualifier() {
        return textQualifier;
    }

    public void setTextQualifier(char textQualifier) {
        this.textQualifier = textQualifier;
    }

    public boolean isIgnoreFirstRecord() {
        return ignoreFirstRecord;
    }

    public void setIgnoreFirstRecord(boolean ignoreFirstRecord) {
        this.ignoreFirstRecord = ignoreFirstRecord;
    }
}
