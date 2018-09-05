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

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.olingo2.api.Olingo2App;
import org.apache.camel.component.olingo2.api.Olingo2ResponseHandler;
import org.apache.camel.util.ObjectHelper;
import org.apache.olingo.odata2.api.edm.Edm;

/**
 * Holder class for {@link org.apache.camel.component.olingo2.api.Olingo2App}
 * and its lazily read {@link org.apache.olingo.odata2.api.edm.Edm}.
 */
public class Olingo2AppWrapper {

    private final Olingo2App olingo2App;
    private volatile Edm edm;

    public Olingo2AppWrapper(Olingo2App olingo2App) {
        ObjectHelper.notNull(olingo2App, "olingo2App");
        this.olingo2App = olingo2App;
    }

    public Olingo2App getOlingo2App() {
        return olingo2App;
    }

    public void close() {
        olingo2App.close();
    }

    // double checked locking based singleton Edm reader
    public Edm getEdm() throws RuntimeCamelException {
        Edm localEdm = edm;
        if (localEdm == null) {

            synchronized (this) {

                localEdm = edm;
                if (localEdm == null) {

                    final CountDownLatch latch = new CountDownLatch(1);
                    final Exception[] error = new Exception[1];
                    olingo2App.read(null, "$metadata", null, null, new Olingo2ResponseHandler<Edm>() {

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
                                final String message = ex.getMessage() != null
                                    ? ex.getMessage() : ex.getClass().getName();
                                throw new RuntimeCamelException("Error reading EDM: " + message, ex);
                            }
                        }

                    } catch (InterruptedException e) {
                        throw new RuntimeCamelException(e.getMessage(), e);
                    }

                    localEdm = edm;
                }
            }
        }

        return localEdm;
    }
}
