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
package org.apache.camel.component.http.handler;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Handler which does not answer until the given latch is released, so tests can deterministically trigger client side
 * timeouts (never release the latch) or get an immediate answer (release the latch before sending the request).
 */
public class BlockingValidationHandler extends BasicValidationHandler {

    private static final int MAX_WAIT_SECONDS = 30;

    private final CountDownLatch release;

    public BlockingValidationHandler(String expectedMethod, String expectedQuery,
                                     Object expectedContent, String responseContent, CountDownLatch release) {
        super(expectedMethod, expectedQuery, expectedContent, responseContent);
        this.release = release;
    }

    @Override
    public void handle(
            final ClassicHttpRequest request, final ClassicHttpResponse response,
            final HttpContext context)
            throws HttpException, IOException {
        try {
            // bounded wait to never hang the server thread forever
            release.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        super.handle(request, response, context);
    }
}
