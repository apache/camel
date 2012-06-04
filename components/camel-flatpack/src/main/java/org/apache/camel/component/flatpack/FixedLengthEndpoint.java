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
import org.apache.camel.processor.loadbalancer.LoadBalancerConsumer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.util.ResourceHelper;

/**
 * A <a href="http://flatpack.sourceforge.net/">Flatpack Endpoint</a>
 * for working with fixed width and delimited files
 *
 * @version 
 */
public class FixedLengthEndpoint extends DefaultPollingEndpoint {
    protected String definition;
    private LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
    private ParserFactory parserFactory = DefaultParserFactory.getInstance();
    private boolean splitRows = true;
    private boolean allowShortLines;
    private boolean ignoreExtraColumns;

    public FixedLengthEndpoint() {
    }

    public FixedLengthEndpoint(String endpointUri, Component component, String definition) {
        super(endpointUri, component);
        this.definition = definition;
    }

    public boolean isSingleton() {
        return true;
    }

    public Producer createProducer() throws Exception {
        return new FlatpackProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new LoadBalancerConsumer(this, processor, loadBalancer);
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
        return createParser(getDefinition(), bodyReader);
    }

    protected Parser createParser(String resourceUri, Reader bodyReader) throws IOException {
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

    // Properties
    //-------------------------------------------------------------------------

    public String getDefinition() {
        return definition;
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
}
