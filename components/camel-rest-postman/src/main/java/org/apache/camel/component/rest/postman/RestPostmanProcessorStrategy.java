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
package org.apache.camel.component.rest.postman;

import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.component.rest.postman.support.PostmanRequestBinding;
import org.apache.camel.support.processor.RestBindingAdvice;
import org.apache.camel.util.json.JsonObject;

/**
 * Strategy for servicing the requests of a Postman collection.
 */
public interface RestPostmanProcessorStrategy {

    /**
     * Whether the consumer should fail, ignore or return a mock response for requests that are not mapped to a
     * corresponding route.
     */
    void setMissingRequest(String missingRequest);

    /**
     * Whether the consumer should fail, ignore or return a mock response for requests that are not mapped to a
     * corresponding route.
     */
    String getMissingRequest();

    /**
     * Used for inclusive filtering of mock data from directories, as Ant-path style patterns separated by comma.
     */
    void setMockIncludePattern(String mockIncludePattern);

    /**
     * Used for inclusive filtering of mock data from directories, as Ant-path style patterns separated by comma.
     */
    String getMockIncludePattern();

    /**
     * Decides which {@code direct} endpoint name a request dispatches to.
     * <p>
     * More than one spelling is accepted, so that a collection fetched from the cloud can be routed by its request id
     * while an exported one is routed by slug. The first candidate with a matching route wins.
     *
     * @param  binding the request
     * @return         the endpoint name, without the {@code direct:} prefix
     */
    String resolveDispatchId(PostmanRequestBinding binding);

    /**
     * Validates the collection on startup.
     *
     * @param  bindings             every request being serviced
     * @param  basePath             the base path they are served under
     * @param  platformHttpConsumer the platform http consumer, when one is in use
     * @throws Exception            if a request has no corresponding route and {@code missingRequest} is {@code fail},
     *                              or if two requests would shadow each other
     */
    default void validateCollection(
            List<PostmanRequestBinding> bindings, String basePath, PlatformHttpConsumerAware platformHttpConsumer)
            throws Exception {
        // noop
    }

    /**
     * Services one matched request.
     *
     * @param  binding    the request being serviced
     * @param  dispatchId the {@code direct} endpoint name to route to
     * @param  verb       the HTTP verb
     * @param  path       the context path
     * @param  advice     the binding advice
     * @param  exchange   the exchange
     * @param  callback   invoked when processing completes
     * @return            (doneSync) true when processing completed synchronously
     */
    boolean process(
            PostmanRequestBinding binding, String dispatchId, String verb, String path,
            RestBindingAdvice advice, Exchange exchange, AsyncCallback callback);

    /**
     * Serves the collection document itself, with credentials already removed.
     *
     * @param  redactedDocument the document to return
     * @param  exchange         the exchange
     * @param  callback         invoked when processing completes
     * @return                  (doneSync) true when processing completed synchronously
     */
    boolean processCollectionDocument(JsonObject redactedDocument, Exchange exchange, AsyncCallback callback);
}
