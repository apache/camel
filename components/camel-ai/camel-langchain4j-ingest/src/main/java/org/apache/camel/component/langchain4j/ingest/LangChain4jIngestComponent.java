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
package org.apache.camel.component.langchain4j.ingest;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Apache Camel component ingesting documents into a LangChain4j {@code EmbeddingStore}: the message body is split into
 * overlapping segments, embedded in batches and written to the store, each segment stamped with the pipeline name and
 * the document id.
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>{@code
 * from("file:/var/data/product-docs?noop=true&recursive=true&readLock=changed")
 *         .to("langchain4j-ingest:products?documentIdHeader=CamelFileName");
 * }</pre>
 *
 * @since 4.23.0
 */
@Component(LangChain4jIngest.SCHEME)
public class LangChain4jIngestComponent extends DefaultComponent {

    @Metadata
    private LangChain4jIngestConfiguration configuration;

    public LangChain4jIngestComponent() {
        this(null);
    }

    public LangChain4jIngestComponent(CamelContext context) {
        super(context);

        this.configuration = new LangChain4jIngestConfiguration();
    }

    public LangChain4jIngestConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The configuration.
     */
    public void setConfiguration(LangChain4jIngestConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(
            String uri,
            String remaining,
            Map<String, Object> parameters)
            throws Exception {

        LangChain4jIngestConfiguration configuration = this.configuration.copy();

        LangChain4jIngestEndpoint endpoint = new LangChain4jIngestEndpoint(uri, this, remaining, configuration);
        setProperties(endpoint, parameters);

        return endpoint;
    }
}
