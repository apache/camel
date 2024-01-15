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

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.olingo4.api.Olingo4App;
import org.apache.camel.component.olingo4.api.Olingo4ResponseHandler;
import org.apache.camel.util.ObjectHelper;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.edm.Edm;

/**
 * Holder class for {@link org.apache.camel.component.olingo4.api.Olingo4App} and its lazily read
 * {@link org.apache.olingo.commons.api.edm.Edm}.
 */
public class Olingo4AppWrapper {

    private final Olingo4App olingo4App;
    private volatile Edm edm;

    public Olingo4AppWrapper(Olingo4App olingo4App) {
        ObjectHelper.notNull(olingo4App, "olingo4App");
        this.olingo4App = olingo4App;
    }

    public Olingo4App getOlingo4App() {
        return olingo4App;
    }

    public void close() {
        olingo4App.close();
    }

    // double checked locking based singleton Edm reader
    public Edm getEdm(Map<String, String> endpointHttpHeaders) throws RuntimeCamelException {
        Edm localEdm = edm;
        if (localEdm == null) {

            synchronized (this) {

                localEdm = edm;
                if (localEdm == null) {

                    final CountDownLatch latch = new CountDownLatch(1);
                    final Exception[] error = new Exception[1];
                    olingo4App.read(null, Constants.METADATA, null, endpointHttpHeaders, new Olingo4ResponseHandler<Edm>() {

                        @Override
                        public void onResponse(Edm response, Map<String, String> responseHeaders) {
                            edm = response;
                            latch.countDown();
                        }

                        @Override
                        public void onException(Exception ex) {
                            error[0] = ex;
                            latch.countDown();
                        }

                        @Override
                        public void onCanceled() {
                            error[0] = new RuntimeCamelException("OData HTTP request cancelled!");
                            latch.countDown();
                        }
                    });

                    try {
                        // wait until response or timeout
                        latch.await();

                        final Exception ex = error[0];
                        if (ex != null) {
                            if (ex instanceof RuntimeCamelException) {
                                throw (RuntimeCamelException) ex;
                            } else {
                                final String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName();
                                throw new RuntimeCamelException("Error reading EDM: " + message, ex);
                            }
                        }

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeCamelException(e.getMessage(), e);
                    }

                    localEdm = edm;
                }
            }
        }

        return localEdm;
    }
}
