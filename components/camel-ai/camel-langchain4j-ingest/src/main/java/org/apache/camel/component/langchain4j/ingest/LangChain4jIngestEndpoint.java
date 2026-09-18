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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Ingest documents into a LangChain4j EmbeddingStore: split, embed and store the message body.
 */
@UriEndpoint(
             firstVersion = "4.23.0",
             scheme = LangChain4jIngest.SCHEME,
             title = "LangChain4j Ingest",
             syntax = "langchain4j-ingest:pipelineName",
             producerOnly = true,
             category = {
                     Category.AI
             },
             headersClass = LangChain4jIngestHeaders.class)
public class LangChain4jIngestEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The pipeline name, stamped on every written segment as the camel_ingest_pipeline"
                           + " metadata and used in error messages")
    private final String pipelineName;

    @UriParam
    private LangChain4jIngestConfiguration configuration;

    public LangChain4jIngestEndpoint(String endpointUri, Component component, String pipelineName,
                                     LangChain4jIngestConfiguration configuration) {

        super(endpointUri, component);
        this.pipelineName = pipelineName;
        this.configuration = configuration;
    }

    public LangChain4jIngestConfiguration getConfiguration() {
        return configuration;
    }

    public String getPipelineName() {
        return pipelineName;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new LangChain4jIngestProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }
}
