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
package org.apache.camel.component.nitrite;

import java.util.function.Consumer;

import org.apache.camel.Exchange;
import org.apache.camel.component.nitrite.operation.CollectionOperation;
import org.apache.camel.component.nitrite.operation.RepositoryOperation;
import org.apache.camel.component.nitrite.operation.common.UpsertOperation;
import org.apache.camel.support.DefaultProducer;
import org.dizitart.no2.NitriteCollection;
import org.dizitart.no2.objects.ObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Nitrite producer.
 */
public class NitriteProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(NitriteProducer.class);
    private NitriteEndpoint endpoint;
    private Consumer<AbstractNitriteOperation> operationValidator = noop -> { };

    public NitriteProducer(NitriteEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;

        if (endpoint.getNitriteCollection() instanceof ObjectRepository) {
            operationValidator = operation -> {
                if (!(operation instanceof RepositoryOperation)) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Attempted to run Collection-only operation %s on Repository %s",
                                    operation.getClass(),
                                    endpoint.getNitriteCollection()
                            )
                    );
                }
            };
        }

        if (endpoint.getNitriteCollection() instanceof NitriteCollection) {
            operationValidator = operation -> {
                if (!(operation instanceof CollectionOperation)) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Attempted to run Repository-only operation %s on Collection %s",
                                    operation.getClass(),
                                    endpoint.getNitriteCollection()
                            )
                    );
                }
            };
        }
    }

    public void process(Exchange exchange) throws Exception {
        AbstractNitriteOperation operation = exchange.getMessage().getHeader(NitriteConstants.OPERATION, AbstractNitriteOperation.class);
        if (operation == null) {
            operation = new UpsertOperation();
        }

        operationValidator.accept(operation);

        operation.execute(exchange, endpoint);
    }

}
