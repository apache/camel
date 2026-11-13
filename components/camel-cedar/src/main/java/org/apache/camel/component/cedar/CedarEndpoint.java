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
package org.apache.camel.component.cedar;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "4.24.0", scheme = "cedar", title = "Cedar", syntax = "cedar:operation",
             producerOnly = true, remote = false, category = { Category.SECURITY })
public class CedarEndpoint extends DefaultEndpoint {
    @UriPath(description = "Operation to perform. Only authorize is supported.")
    @Metadata(required = true, enums = "authorize")
    private String operation = "authorize";
    @UriParam
    private CedarConfiguration configuration;
    private volatile CedarEvaluator evaluator;

    public CedarEndpoint(String uri, CedarComponent component, CedarConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public CedarConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(CedarConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public CedarEvaluator getEvaluator() {
        return evaluator;
    }

    /** Validate and atomically publish a new configured resource snapshot. */
    public void reload() throws Exception {
        CedarEvaluator current = evaluator;
        if (current == null) {
            throw new IllegalStateException("Cedar endpoint is not started");
        }
        current.reload();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!"authorize".equals(operation)) {
            throw new IllegalArgumentException("Cedar supports only authorize");
        }
        evaluator = new CedarEvaluator(getCamelContext(), configuration);
        evaluator.start();
    }

    @Override
    protected void doStop() throws Exception {
        try {
            if (evaluator != null) {
                evaluator.stop();
            }
        } finally {
            evaluator = null;
            super.doStop();
        }
    }

    @Override
    public Producer createProducer() {
        return new CedarProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Cedar is producer only");
    }
}
