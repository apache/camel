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
package org.apache.camel.component.salesforce.internal.client;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.salesforce.api.SalesforceException;

/**
 * Thin wrapper to handle callback for {@link RestClient.ResponseCallback} and allow waiting for results
 */
public class SyncResponseCallback implements RestClient.ResponseCallback {

    private InputStream response;
    private SalesforceException exception;
    private Map<String, String> headers;
    private CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void onResponse(InputStream response, Map<String, String> headers, SalesforceException exception) {
        this.response = response;
        this.headers = headers;
        this.exception = exception;
        latch.countDown();
    }

    public void reset() {
        latch = new CountDownLatch(1);
    }

    public boolean await(long duration, TimeUnit unit) throws InterruptedException {
        return latch.await(duration, unit);
    }

    public InputStream getResponse() {
        return response;
    }

    public SalesforceException getException() {
        return exception;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }
}
