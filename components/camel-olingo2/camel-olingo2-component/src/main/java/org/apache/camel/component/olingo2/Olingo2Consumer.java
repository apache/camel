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
package org.apache.camel.component.olingo2;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.olingo2.api.Olingo2ResponseHandler;
import org.apache.camel.component.olingo2.internal.Olingo2ApiName;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.component.AbstractApiConsumer;
import org.apache.camel.util.component.ApiConsumerHelper;

/**
 * The Olingo2 consumer.
 */
public class Olingo2Consumer extends AbstractApiConsumer<Olingo2ApiName, Olingo2Configuration> {

    public Olingo2Consumer(Olingo2Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        // invoke the consumer method
        final Map<String, Object> args = new HashMap<String, Object>();
        args.putAll(endpoint.getEndpointProperties());

        // let the endpoint and the Consumer intercept properties
        endpoint.interceptProperties(args);
        interceptProperties(args);

        try {
            // create responseHandler
            final CountDownLatch latch = new CountDownLatch(1);
            final Object[] result = new Object[1];
            final Exception[] error = new Exception[1];

            args.put(Olingo2Endpoint.RESPONSE_HANDLER_PROPERTY, new Olingo2ResponseHandler<Object>() {
                @Override
                public void onResponse(Object response, Map<String, String> responseHeaders) {
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

            // guaranteed to return, since an exception on timeout is expected!!!
            latch.await();

            if (error[0] != null) {
                throw error[0];
            }

            return ApiConsumerHelper.getResultsProcessed(this, result[0], isSplitResult());

        } catch (Throwable t) {
            throw ObjectHelper.wrapRuntimeCamelException(t);
        }
    }

}
