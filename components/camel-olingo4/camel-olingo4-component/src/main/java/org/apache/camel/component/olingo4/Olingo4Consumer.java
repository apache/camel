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
package org.apache.camel.component.olingo4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.olingo4.api.Olingo4ResponseHandler;
import org.apache.camel.component.olingo4.internal.Olingo4ApiName;
import org.apache.camel.support.component.AbstractApiConsumer;
import org.apache.camel.support.component.ApiConsumerHelper;
import org.apache.olingo.client.api.domain.ClientCollectionValue;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.domain.ClientValue;
import org.apache.olingo.client.core.domain.ClientPrimitiveValueImpl;
import org.apache.olingo.client.core.domain.ClientPropertyImpl;

/**
 * The Olingo4 consumer.
 */
public class Olingo4Consumer extends AbstractApiConsumer<Olingo4ApiName, Olingo4Configuration> {

    private Olingo4Index resultIndex;

    public Olingo4Consumer(Olingo4Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        // invoke the consumer method
        final Map<String, Object> args = new HashMap<>(endpoint.getEndpointProperties());

        // let the endpoint and the Consumer intercept properties
        endpoint.interceptProperties(args);
        interceptProperties(args);

        try {
            // create responseHandler
            final CountDownLatch latch = new CountDownLatch(1);
            final Object[] result = new Object[1];
            final Exception[] error = new Exception[1];

            args.put(Olingo4Endpoint.RESPONSE_HANDLER_PROPERTY, new Olingo4ResponseHandler<Object>() {
                @Override
                public void onResponse(Object response, Map<String, String> responseHeaders) {
                    if (resultIndex != null) {
                        response = resultIndex.filterResponse(response);
                    }

                    result[0] = response;
                    latch.countDown();
                }

                @Override
                public void onException(Exception ex) {
                    error[0] = ex;
                    latch.countDown();
                }

                @Override
                public void onCanceled() {
                    error[0] = new RuntimeCamelException("OData HTTP Request cancelled");
                    latch.countDown();
                }
            });

            doInvokeMethod(args);

            // guaranteed to return, since an exception on timeout is
            // expected!!!
            latch.await();

            if (error[0] != null) {
                throw error[0];
            }

            //
            // Allow consumer idle properties to properly handle an empty
            // polling response
            //
            if (result[0] == null
                    || result[0] instanceof ClientEntitySet && (((ClientEntitySet) result[0]).getEntities().isEmpty())) {
                return 0;
            } else {
                return ApiConsumerHelper.getResultsProcessed(this, result[0], isSplitResult());
            }

        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public void interceptProperties(Map<String, Object> properties) {
        //
        // If we have a filterAlreadySeen property then initialise the filter
        // index
        //
        Object value = properties.get(Olingo4Endpoint.FILTER_ALREADY_SEEN);
        if (value == null) {
            return;
        }

        //
        // Initialise the index if not already and if filterAlreadySeen has been
        // set
        //
        if (Boolean.parseBoolean(value.toString()) && resultIndex == null) {
            resultIndex = new Olingo4Index();
        }
    }

    @Override
    public void interceptResult(Object result, Exchange resultExchange) {
        if (resultIndex == null) {
            return;
        }

        resultIndex.index(result);
    }

    @Override
    public Object splitResult(Object result) {
        List<Object> splitResult = new ArrayList<>();

        if (result instanceof ClientEntitySet) {
            ClientEntitySet entitySet = (ClientEntitySet) result;
            for (ClientEntity entity : entitySet.getEntities()) {
                //
                // If $count has been set to true then this value is left behind
                // on the ClientEntitySet. Therefore, append it to each result.
                //
                if (entitySet.getCount() != null) {
                    ClientValue value = new ClientPrimitiveValueImpl.BuilderImpl().buildInt32(entitySet.getCount());
                    entity.getProperties().add(new ClientPropertyImpl("ResultCount", value));
                }
                splitResult.add(entity);
            }
        } else if (result instanceof ClientValue && ((ClientValue) result).isCollection()) {
            ClientValue value = (ClientValue) result;
            ClientCollectionValue<ClientValue> collection = value.asCollection();
            collection.forEach(v -> {
                splitResult.add(v);
            });
        } else {
            splitResult.add(result);
        }

        return splitResult;
    }
}
