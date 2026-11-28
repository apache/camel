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

import java.util.Collections;
import java.util.List;

import dev.langchain4j.rag.content.Content;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What a source does when its route fails, is rejected, times out or returns a body that cannot be converted. A
 * {@link VirtualMachineError} and an interrupt of the caller are always thrown.
 */
public enum OnError {

    /** Fail the retrieval, and with it the answer, with a {@link CamelExecutionException}. */
    FAIL,
    /** Return no content for this source. */
    EMPTY;

    private static final Logger LOG = LoggerFactory.getLogger(OnError.class);

    List<Content> failure(String source, RetrievalFailure reason, String what, Exchange exchange, Throwable cause) {
        if (this == EMPTY) {
            LOG.warn("Content source '{}' {} ({}): returning no content: {}", source, what, reason, cause.toString());
            return Collections.emptyList();
        }
        throw new CamelExecutionException("Content source '" + source + "' " + what, exchange, cause);
    }
}
