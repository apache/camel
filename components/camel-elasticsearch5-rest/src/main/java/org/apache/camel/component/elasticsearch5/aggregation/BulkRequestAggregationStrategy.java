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
package org.apache.camel.component.elasticsearch5.aggregation;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadRuntimeException;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkRequest;

/**
 * Aggregates two {@link ActionRequest}s into a single {@link BulkRequest}.
 */
public class BulkRequestAggregationStrategy implements AggregationStrategy {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        // Don't use getBody(Class<T>) here as we don't want to coerce the body type using a type converter.
        Object objBody = newExchange.getIn().getBody();
        if (!(objBody instanceof DocWriteRequest[])) {
            throw new InvalidPayloadRuntimeException(newExchange, DocWriteRequest[].class);
        }

        DocWriteRequest[] newBody = (DocWriteRequest[]) objBody;
        BulkRequest request;
        if (oldExchange == null) {
            request = new BulkRequest();
            request.add(newBody);
            newExchange.getIn().setBody(request);
            return newExchange;
        } else {
            request = oldExchange.getIn().getBody(BulkRequest.class);
            request.add(newBody);
            return oldExchange;
        }
    }
}
