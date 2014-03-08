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
package org.apache.camel.component.lucene;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

public class LuceneIndexProducer extends DefaultProducer {
    LuceneConfiguration config;
    LuceneIndexer indexer;
    
    public LuceneIndexProducer(Endpoint endpoint, LuceneConfiguration config, LuceneIndexer indexer) throws Exception {
        super(endpoint);
        this.config = config;
        this.indexer = indexer;
    }
    
    public void start() throws Exception {
        super.doStart();
    }

    public void stop() throws Exception {
        this.indexer.getNiofsDirectory().close();
        super.doStop();
    }

    public void process(Exchange exchange) throws Exception {
        indexer.index(exchange);
    }

    public LuceneConfiguration getConfig() {
        return config;
    }

    public void setConfig(LuceneConfiguration config) {
        this.config = config;
    }

    public LuceneIndexer getIndexer() {
        return indexer;
    }

    public void setIndexer(LuceneIndexer indexer) {
        this.indexer = indexer;
    }   

}
