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
package org.apache.camel.component.nitrite.operation.repository;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.component.nitrite.NitriteConstants;
import org.apache.camel.component.nitrite.NitriteEndpoint;
import org.apache.camel.component.nitrite.operation.AbstractPayloadAwareOperation;
import org.apache.camel.component.nitrite.operation.RepositoryOperation;
import org.dizitart.no2.objects.ObjectFilter;
import org.dizitart.no2.objects.ObjectRepository;

/**
 * Update objects matching ObjectFilter. If payload not specified, the message body is used
 */
public class UpdateRepositoryOperation extends AbstractPayloadAwareOperation implements RepositoryOperation {

    private ObjectFilter filter;

    public UpdateRepositoryOperation(ObjectFilter filter) {
        super();
        this.filter = filter;
    }

    public UpdateRepositoryOperation(ObjectFilter filter, Object payload) {
        super(payload);
        this.filter = filter;
    }
    public UpdateRepositoryOperation(ObjectFilter filter, Expression documentExpression) {
        super(documentExpression);
        this.filter = filter;
    }

    @Override
    protected void execute(Exchange exchange, NitriteEndpoint endpoint) throws Exception {
        ObjectRepository repository = (ObjectRepository) endpoint.getNitriteCollection();
        Object payload = getPayload(exchange, endpoint);
        if (filter != null) {
            exchange.getMessage().setHeader(NitriteConstants.WRITE_RESULT, repository.update(filter, payload));
        } else {
            exchange.getMessage().setHeader(NitriteConstants.WRITE_RESULT, repository.update(payload));
        }
    }

}
