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
package org.apache.camel.component.nitrite.operation.collection;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.component.nitrite.NitriteConstants;
import org.apache.camel.component.nitrite.NitriteEndpoint;
import org.apache.camel.component.nitrite.operation.AbstractPayloadAwareOperation;
import org.apache.camel.component.nitrite.operation.CollectionOperation;
import org.dizitart.no2.Document;
import org.dizitart.no2.Filter;
import org.dizitart.no2.NitriteCollection;
import org.dizitart.no2.UpdateOptions;

/**
 * Update documents matching Filter. If Document not specified, the message body is used
 */
public class UpdateCollectionOperation extends AbstractPayloadAwareOperation implements CollectionOperation {

    private Filter filter;
    private UpdateOptions updateOptions;

    public UpdateCollectionOperation(Filter filter) {
        super();
        this.filter = filter;
    }

    public UpdateCollectionOperation(Filter filter, UpdateOptions updateOptions) {
        super();
        this.filter = filter;
        this.updateOptions = updateOptions;
    }

    public UpdateCollectionOperation(Filter filter, Document document) {
        super(document);
        this.filter = filter;
    }

    public UpdateCollectionOperation(Filter filter, UpdateOptions updateOptions, Document document) {
        super(document);
        this.filter = filter;
        this.updateOptions = updateOptions;
    }

    public UpdateCollectionOperation(Filter filter, Expression documentExpression) {
        super(documentExpression);
        this.filter = filter;
    }

    public UpdateCollectionOperation(Filter filter, UpdateOptions updateOptions, Expression documentExpression) {
        super(documentExpression);
        this.filter = filter;
        this.updateOptions = updateOptions;
    }

    @Override
    protected void execute(Exchange exchange, NitriteEndpoint endpoint) throws Exception {
        NitriteCollection collection = (NitriteCollection) endpoint.getNitriteCollection();
        Document payload = (Document) getPayload(exchange, endpoint);
        if (filter != null && updateOptions != null) {
            exchange.getMessage().setHeader(NitriteConstants.WRITE_RESULT, collection.update(filter, payload, updateOptions));
        } else {
            exchange.getMessage().setHeader(NitriteConstants.WRITE_RESULT, collection.update(filter, payload));
        }
    }

}
