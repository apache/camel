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

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * To insert or query from Apache Lucene databases.
 */
@UriEndpoint(firstVersion = "2.2.0", scheme = "lucene", title = "Lucene", syntax = "lucene:host:operation", producerOnly = true, label = "database,search")
public class LuceneEndpoint extends DefaultEndpoint {
    @UriParam
    LuceneConfiguration config;
    LuceneIndexer indexer;
    boolean insertFlag;

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
    
    public LuceneConfiguration getConfig() {
        return config;
    }

    public void setConfig(LuceneConfiguration config) {
        this.config = config;
    }

}
