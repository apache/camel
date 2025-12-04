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

package org.apache.camel.component.rest.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.support.processor.RestBindingAdvice;

/**
 * Strategy for processing the Rest DSL that services an OpenAPI spec.
 */
public interface RestOpenapiProcessorStrategy {

    /**
     * Whether the consumer should fail,ignore or return a dummy response for OpenAPI operations that are not mapped to
     * a corresponding route.
     */
    void setMissingOperation(String missingOperation);

    /**
     * Whether the consumer should fail,ignore or return a dummy response for OpenAPI operations that are not mapped to
     * a corresponding route.
     */
    String getMissingOperation();

    /**
     * Used for inclusive filtering of mock data from directories. The pattern is using Ant-path style pattern. Multiple
     * patterns can be specified separated by comma.
     */
    void setMockIncludePattern(String mockIncludePattern);

    /**
     * Used for inclusive filtering of mock data from directories. The pattern is using Ant-path style pattern. Multiple
     * patterns can be specified separated by comma.
     */
    String getMockIncludePattern();

    /**
     * Validates the OpenAPI specification on startup
     *
     * @param  openAPI              the openapi specification
     * @param  basePath             base path
     * @param  platformHttpConsumer the platform http consumer
     * @throws Exception            is thrown if validation error on startup
     */
    default void validateOpenApi(OpenAPI openAPI, String basePath, PlatformHttpConsumerAware platformHttpConsumer)
            throws Exception {
        // noop
    }

    /**
     * Strategy for processing the Rest DSL operation
     *
     * @param  openAPI   the openapi specification
     * @param  operation the rest operation
     * @param  verb      the HTTP verb (GET, POST etc.)
     * @param  path      the context-path
     * @param  binding   binding advice
     * @param  exchange  the exchange
     * @param  callback  the AsyncCallback will be invoked when the processing of the exchange is completed. If the
     *                   exchange is completed synchronously, then the callback is also invoked synchronously. The
     *                   callback should therefore be careful of starting recursive loop.
     * @return           (doneSync) true to continue to execute synchronously, false to continue being executed
     *                   asynchronously
     */
    boolean process(
            OpenAPI openAPI,
            Operation operation,
            String verb,
            String path,
            RestBindingAdvice binding,
            Exchange exchange,
            AsyncCallback callback);

    /**
     * Strategy for processing the OpenAPI specification (to return the contract)
     *
     * @param  exchange the exchange
     * @param  callback the AsyncCallback will be invoked when the processing of the exchange is completed. If the
     *                  exchange is completed synchronously, then the callback is also invoked synchronously. The
     *                  callback should therefore be careful of starting recursive loop.
     * @return          (doneSync) true to continue to execute synchronously, false to continue being executed
     *                  asynchronously
     */
    boolean processApiSpecification(String specificationUri, Exchange exchange, AsyncCallback callback);
}
