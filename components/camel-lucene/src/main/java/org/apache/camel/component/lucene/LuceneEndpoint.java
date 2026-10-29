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
package org.apache.camel.component.lucene;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Perform inserts or queries against Apache Lucene databases.
 */
@UriEndpoint(firstVersion = "2.2.0", scheme = "lucene", title = "Lucene", syntax = "lucene:host:operation", producerOnly = true,
             category = { Category.DATABASE, Category.SEARCH }, headersClass = LuceneConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = @BodyType.When(option = "operation", value = "insert", carriers = {
                                          Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }),
                                  description = "insert indexes the body as text in the contents field: bytes, a stream or"
                                                + " a file are read as text, another object as its toString(), and no body"
                                                + " fails; each header is indexed too, as a field of its name. query does not"
                                                + " use the body: the query is the CamelLuceneQuery header"),
           producerOut = @BodyType(value = { Carrier.UNCHANGED, Carrier.POJO },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "insert",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "query", carriers = Carrier.POJO,
                                                          javaType = "org.apache.camel.processor.lucene.support.Hits") },
                                   description = "insert leaves the body unchanged. query gives the Hits: the number of hits"
                                                 + " and a List of Hit with the score and the contents of each document found"
                                                 + " (and the Lucene Document with the CamelLuceneReturnLuceneDocs header)"))
public class LuceneEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private LuceneIndexer indexer;
    private boolean insertFlag;

    @UriParam
    LuceneConfiguration config;

    public LuceneEndpoint() {
    }

    public LuceneEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public LuceneEndpoint(String endpointUri, LuceneComponent component, LuceneConfiguration config) throws Exception {
        this(endpointUri, component);
        this.config = config;
        if (config.getOperation() == LuceneOperation.insert) {
            this.indexer = new LuceneIndexer(config.getSrcDir(), config.getIndexDir(), config.getAnalyzer());
            insertFlag = true;
        }
    }

    @Override
    public String getServiceUrl() {
        if (config != null) {
            return config.getHost();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "lucene";
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for Lucene endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        if (!insertFlag) {
            return new LuceneQueryProducer(this, this.config);
        }
        return new LuceneIndexProducer(this, this.config, indexer);
    }

    @Override
    protected void doShutdown() throws Exception {
        // the index directory is shared by all producers of this endpoint, so it is closed with the endpoint
        // and not when a producer (route) stops
        if (indexer != null) {
            indexer.getNiofsDirectory().close();
        }
        super.doShutdown();
    }

    public LuceneConfiguration getConfig() {
        return config;
    }

    public void setConfig(LuceneConfiguration config) {
        this.config = config;
    }

}
