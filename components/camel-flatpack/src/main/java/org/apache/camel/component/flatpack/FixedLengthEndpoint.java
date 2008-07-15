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
import java.io.InputStreamReader;
import java.io.Reader;

import net.sf.flatpack.DataSet;
import net.sf.flatpack.DefaultParserFactory;
import net.sf.flatpack.Parser;
import net.sf.flatpack.ParserFactory;
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
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.springframework.core.io.Resource;

/**
 * A <a href="http://flatpack.sourceforge.net/">Flatpack Endpoint</a>
 * for working with fixed width and delimited files
 *
 * @version $Revision: 1.1 $
 */
public class FixedLengthEndpoint extends DefaultPollingEndpoint<Exchange> {
    private final Resource resource;
    private LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
    private ParserFactory parserFactory = DefaultParserFactory.getInstance();

    public FixedLengthEndpoint(String uri, Resource resource) {
        super(uri);
        this.resource = resource;
    }

    public boolean isSingleton() {
        return true;
    }

    public Producer<Exchange> createProducer() throws Exception {
        return new FlatpackProducer(this);
    }

    public Consumer<Exchange> createConsumer(Processor processor) throws Exception {
        return new LoadBalancerConsumer(this, processor, loadBalancer);
    }

    public void processDataSet(DataSet dataSet, int counter) throws Exception {
        Exchange exchange = createExchange(dataSet, counter);
        loadBalancer.process(exchange);
    }

    public Exchange createExchange(DataSet dataSet, int counter) {
        Exchange answer = createExchange();
        Message in = answer.getIn();
        in.setBody(dataSet);
        in.setHeader("camelFlatpackCounter", counter);
        return answer;
    }

    public Parser createParser(Exchange exchange) throws InvalidPayloadException, IOException {
        Resource resource = getResource();
        ObjectHelper.notNull(resource, "endpoint.resource");
        Reader bodyReader = ExchangeHelper.getMandatoryInBody(exchange, Reader.class);
        return createParser(resource, bodyReader);
    }

    protected Parser createParser(Resource resource, Reader bodyReader) throws IOException {
        return getParserFactory().newFixedLengthParser(new InputStreamReader(resource.getInputStream()), bodyReader);
    }

    // Properties
    //-------------------------------------------------------------------------

    public Resource getResource() {
        return resource;
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

}
