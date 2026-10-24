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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * Process documents using Docling library for parsing and conversion.
 */
@UriEndpoint(firstVersion = "4.15.0", scheme = "docling",
             title = "Docling",
             syntax = "docling:operationId",
             category = { Category.TRANSFORMATION, Category.AI }, headersClass = DoclingHeaders.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.TEXT,
                                   description = "The document of an async conversion that completed"
                                                 + " (SUBMIT_ASYNC_CONVERSION on the same component), as text in the"
                                                 + " outputFormat it was submitted with: markdown by default, JSON text for"
                                                 + " json. The task id is in the CamelDoclingTaskId header, and a failed"
                                                 + " conversion goes to the error handler"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "BATCH_CONVERT_TO_MARKDOWN",
                                                         carriers = Carrier.LIST),
                                          @BodyType.When(option = "operation", value = "BATCH_CONVERT_TO_HTML",
                                                         carriers = Carrier.LIST),
                                          @BodyType.When(option = "operation", value = "BATCH_CONVERT_TO_JSON",
                                                         carriers = Carrier.LIST),
                                          @BodyType.When(option = "operation", value = "BATCH_EXTRACT_TEXT",
                                                         carriers = Carrier.LIST),
                                          @BodyType.When(option = "operation", value = "BATCH_EXTRACT_STRUCTURED_DATA",
                                                         carriers = Carrier.LIST) },
                                  description = "The document: its content as text or bytes, or a File (the GenericFile of"
                                                + " the file consumers too). A stream or a Path fails: convert it to bytes"
                                                + " first. Text that looks like a URL or a path (it starts with http://,"
                                                + " https:// or /, or has a backslash) is read as one only with"
                                                + " allowUrlSource or allowFilePathSource, and fails otherwise. With the"
                                                + " CamelDoclingInputFilePath header the body is not read. The batch"
                                                + " operations take a List or an array of paths or files (a directory path"
                                                + " with allowFilePathSource), and CHECK_CONVERSION_STATUS the task id, unless"
                                                + " the CamelDoclingTaskId header gives it"),
           producerOut = @BodyType(value = Carrier.TEXT,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "CONVERT_TO_JSON",
                                                          carriers = Carrier.POJO,
                                                          javaType = "ai.docling.core.DoclingDocument"),
                                           @BodyType.When(option = "operation", value = "EXTRACT_STRUCTURED_DATA",
                                                          carriers = Carrier.POJO,
                                                          javaType = "ai.docling.core.DoclingDocument"),
                                           @BodyType.When(option = "operation", value = "CHECK_CONVERSION_STATUS",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.apache.camel.component.docling.ConversionStatus"),
                                           @BodyType.When(option = "operation", value = "EXTRACT_METADATA",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.apache.camel.component.docling.DocumentMetadata"),
                                           @BodyType.When(option = "operation", value = "BATCH_CONVERT_TO_MARKDOWN",
                                                          carriers = { Carrier.POJO, Carrier.LIST }),
                                           @BodyType.When(option = "operation", value = "BATCH_CONVERT_TO_HTML",
                                                          carriers = { Carrier.POJO, Carrier.LIST }),
                                           @BodyType.When(option = "operation", value = "BATCH_CONVERT_TO_JSON",
                                                          carriers = { Carrier.POJO, Carrier.LIST }),
                                           @BodyType.When(option = "operation", value = "BATCH_EXTRACT_TEXT",
                                                          carriers = { Carrier.POJO, Carrier.LIST }),
                                           @BodyType.When(option = "operation", value = "BATCH_EXTRACT_STRUCTURED_DATA",
                                                          carriers = { Carrier.POJO, Carrier.LIST }),
                                           @BodyType.When(option = "operation", value = "CHUNK_HYBRID",
                                                          carriers = { Carrier.POJO, Carrier.LIST }),
                                           @BodyType.When(option = "operation", value = "CHUNK_HIERARCHICAL",
                                                          carriers = { Carrier.POJO, Carrier.LIST }) },
                                   description = "By the operation: text for the conversions to markdown, HTML and text,"
                                                 + " which with the docling CLI is the path of the output file unless"
                                                 + " contentInBody (useDoclingServe gives the content); the task id for"
                                                 + " SUBMIT_ASYNC_CONVERSION; a DoclingDocument for CONVERT_TO_JSON and"
                                                 + " EXTRACT_STRUCTURED_DATA; a BatchProcessingResults for the batch"
                                                 + " operations, a List of BatchConversionResult with splitBatchResults; and a"
                                                 + " ChunkDocumentResponse for the chunking, a List of Chunk with"
                                                 + " contentInBody"))
public class DoclingEndpoint extends ScheduledPollEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The operation identifier")
    private final String operationId;

    @UriParam
    private DoclingConfiguration configuration;

    public DoclingEndpoint(String uri, DoclingComponent component, String operationId, DoclingConfiguration configuration) {
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
        DoclingConsumer consumer = new DoclingConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
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
