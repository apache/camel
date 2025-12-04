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

package org.apache.camel.component.docling;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Process documents using Docling library for parsing and conversion.
 */
@UriEndpoint(
        firstVersion = "4.15.0",
        scheme = "docling",
        title = "Docling",
        syntax = "docling:operationId",
        category = {Category.TRANSFORMATION, Category.AI},
        headersClass = DoclingHeaders.class,
        producerOnly = true)
public class DoclingEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The operation identifier")
    private final String operationId;

    @UriParam
    private DoclingConfiguration configuration;

    public DoclingEndpoint(
            String uri, DoclingComponent component, String operationId, DoclingConfiguration configuration) {
        super(uri, component);
        this.operationId = operationId;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new DoclingProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for Docling component");
    }

    public String getOperationId() {
        return operationId;
    }

    public DoclingConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(DoclingConfiguration configuration) {
        this.configuration = configuration;
    }
}
