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
package org.apache.camel.component.langchain4j.core.rag;

/**
 * Why a retrieval did not produce content from its route. Each one follows the {@link OnError} policy and is counted by
 * {@link CamelContentRetriever#getFailureCount(RetrievalFailure)}.
 */
public enum RetrievalFailure {

    /** The call was rejected: {@code concurrency} calls running and {@code queueCapacity} calls queued. */
    REJECTED,
    /** The retriever stopped before the call reached the route. */
    STOPPED,
    /** The route did not answer within {@code timeout}. */
    TIMEOUT,
    /** The route failed with an exception. */
    ROUTE_FAILURE,
    /** The route answered with a body that cannot be converted to content. */
    CONVERSION
}
