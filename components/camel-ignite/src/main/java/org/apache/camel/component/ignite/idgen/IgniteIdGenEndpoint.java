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
package org.apache.camel.component.ignite.idgen;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ignite.AbstractIgniteEndpoint;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.ignite.IgniteAtomicSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.ignite.IgniteConstants.SCHEME_IDGEN;

/**
 * Interact with <a href="https://apacheignite.readme.io/docs/id-generator">Ignite Atomic Sequences and ID Generators
 * </a>.
 *
 * This endpoint only supports producers.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = SCHEME_IDGEN, title = "Ignite ID Generator", syntax = "ignite-idgen:name",
             category = { Category.CACHE, Category.CLUSTERING }, producerOnly = true, headersClass = IgniteConstants.class)
public class IgniteIdGenEndpoint extends AbstractIgniteEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(IgniteIdGenEndpoint.class);

    @UriPath
    @Metadata(required = true)
    private String name;

    @UriParam(label = "producer")
    private Integer batchSize;

    @UriParam(label = "producer", defaultValue = "0")
    private Long initialValue = 0L;

    @UriParam(label = "producer")
    private IgniteIdGenOperation operation;

    public IgniteIdGenEndpoint(String endpointUri, String remaining, Map<String, Object> parameters,
                               IgniteIdGenComponent igniteComponent) {
        super(endpointUri, igniteComponent);
        name = remaining;

        ObjectHelper.notNull(name, "ID Generator name");
    }

    @Override
    public Producer createProducer() throws Exception {
        IgniteAtomicSequence atomicSeq = ignite().atomicSequence(name, initialValue, false);

        if (atomicSeq == null) {
            atomicSeq = ignite().atomicSequence(name, initialValue, true);
            LOG.info("Created AtomicSequence of ID Generator with name {}.", name);
        }

        if (batchSize != null) {
            atomicSeq.batchSize(batchSize);
        }

        return new IgniteIdGenProducer(this, atomicSeq);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("The Ignite Id Generator endpoint doesn't support consumers.");
    }

    /**
     * Gets the name.
     */
    public String getName() {
        return name;
    }

    /**
     * The sequence name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the initial value.
     */
    public Long getInitialValue() {
        return initialValue;
    }

    /**
     * The initial value.
     */
    public void setInitialValue(Long initialValue) {
        this.initialValue = initialValue;
    }

    /**
     * Gets the operation.
     */
    public IgniteIdGenOperation getOperation() {
        return operation;
    }

    /**
     * The operation to invoke on the Ignite ID Generator. Superseded by the IgniteConstants.IGNITE_IDGEN_OPERATION
     * header in the IN message. Possible values: ADD_AND_GET, GET, GET_AND_ADD, GET_AND_INCREMENT, INCREMENT_AND_GET.
     */
    public void setOperation(IgniteIdGenOperation operation) {
        this.operation = operation;
    }

    /**
     * Gets the batch size.
     */
    public Integer getBatchSize() {
        return batchSize;
    }

    /**
     * The batch size.
     */
    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

}
