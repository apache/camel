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

package org.apache.camel.spi;

/**
 * Strategy for intercepting sending messages to endpoints.
 *
 * The strategy can match by uri or pattern, and determine whether to skip sending the message to the original intended
 * endpoints.
 *
 * This is used by camel-test for the auto mocking feature (such as @MockEndpoint). See the
 * org.apache.camel.processor.AutoMockInterceptProducer.
 */
public interface AutoMockInterceptStrategy {

    /**
     * Intercept sending to the uri or uri pattern.
     */
    void setPattern(String pattern);

    /**
     * Intercept sending to the uri or uri pattern.
     */
    String getPattern();

    /**
     * Whether to skip sending to the original endpoint.
     */
    boolean isSkip();

    /**
     * Whether to skip sending to the original endpoint.
     */
    void setSkip(boolean skip);
}
