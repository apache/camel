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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

/**
 * Used for components that can optimise the usage of {@link org.apache.camel.processor.SendDynamicProcessor} (toD)
 * to reuse a static {@link org.apache.camel.Endpoint} and {@link Producer} that supports
 * using headers to provide the dynamic parts. For example many of the HTTP components supports this.
 */
public interface SendDynamicAware {

    /**
     * Sets the component name.
     *
     * @param scheme  name of the component
     */
    void setScheme(String scheme);

    /**
     * Gets the component name
     */
    String getScheme();

    /**
     * An entry of detailed information from the recipient uri, which allows the {@link SendDynamicAware}
     * implementation to prepare pre- and post- processor and the static uri to be used for the optimised dynamic to.
     */
    class DynamicAwareEntry {

        private final String uri;
        private final String originalUri;
        private final Map<String, String> properties;
        private final Map<String, String> lenientProperties;

        public DynamicAwareEntry(String uri, String originalUri, Map<String, String> properties, Map<String, String> lenientProperties) {
            this.uri = uri;
            this.originalUri = originalUri;
            this.properties = properties;
            this.lenientProperties = lenientProperties;
        }

        public String getUri() {
            return uri;
        }

        public String getOriginalUri() {
            return originalUri;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public Map<String, String> getLenientProperties() {
            return lenientProperties;
        }
    }

    /**
     * Prepares for using optimised dynamic to by parsing the uri and returning an entry of details that are
     * used for creating the pre and post processors, and the static uri.
     *
     * @param exchange     the exchange
     * @param uri          the resolved uri which is intended to be used
     * @param originalUri  the original uri of the endpoint before any dynamic evaluation
     * @return prepared information about the dynamic endpoint to use
     * @throws Exception is thrown if error parsing the uri
     */
    DynamicAwareEntry prepare(Exchange exchange, String uri, String originalUri) throws Exception;

    /**
     * Resolves the static part of the uri that are used for creating a single {@link org.apache.camel.Endpoint}
     * and {@link Producer} that will be reused for processing the optimised toD.
     *
     * @param exchange    the exchange
     * @param entry       prepared information about the dynamic endpoint to use
     * @return the static uri, or <tt>null</tt> to not let toD use this optimisation.
     * @throws Exception is thrown if error resolving the static uri.
     */
    String resolveStaticUri(Exchange exchange, DynamicAwareEntry entry) throws Exception;

    /**
     * Creates the pre {@link Processor} that will prepare the {@link Exchange}
     * with dynamic details from the given recipient.
     *
     * @param exchange    the exchange
     * @param entry       prepared information about the dynamic endpoint to use
     * @return the processor, or <tt>null</tt> to not let toD use this optimisation.
     * @throws Exception is thrown if error creating the pre processor.
     */
    Processor createPreProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception;

    /**
     * Creates an optional post {@link Processor} that will be executed afterwards
     * when the message has been sent dynamic.
     *
     * @param exchange    the exchange
     * @param entry       prepared information about the dynamic endpoint to use
     * @return the post processor, or <tt>null</tt> if no post processor is needed.
     * @throws Exception is thrown if error creating the post processor.
     */
    Processor createPostProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception;

}
