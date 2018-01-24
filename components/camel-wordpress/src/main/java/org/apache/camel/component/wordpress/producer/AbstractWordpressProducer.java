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
package org.apache.camel.component.wordpress.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.wordpress.WordpressComponentConfiguration;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.apache.camel.component.wordpress.api.WordpressServiceProvider;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractWordpressProducer<T> extends DefaultProducer {

    protected static final Logger LOG = LoggerFactory.getLogger(WordpressPostProducer.class);

    private WordpressComponentConfiguration configuration;

    public AbstractWordpressProducer(WordpressEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfig();
        if (!WordpressServiceProvider.getInstance().hasAuthentication()) {
            LOG.warn("Wordpress Producer hasn't authentication. This may lead to errors during route execution. Wordpress writing operations need authentication.");
        }
    }

    public WordpressComponentConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public WordpressEndpoint getEndpoint() {
        return (WordpressEndpoint)super.getEndpoint();
    }

    @Override
    public final void process(Exchange exchange) throws Exception {
        if (this.getConfiguration().getId() == null) {
            exchange.getOut().setBody(this.processInsert(exchange));
        } else {
            if (this.getEndpoint().getOperationDetail() == null) {
                exchange.getOut().setBody(this.processUpdate(exchange));
            } else {
                exchange.getOut().setBody(this.processDelete(exchange));
            }
        }
    }

    protected abstract T processInsert(Exchange exchange);

    protected abstract T processUpdate(Exchange exchange);

    protected abstract T processDelete(Exchange exchange);

}
