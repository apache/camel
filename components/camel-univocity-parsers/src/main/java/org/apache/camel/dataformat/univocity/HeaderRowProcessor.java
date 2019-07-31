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
package org.apache.camel.dataformat.univocity;

import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.processor.RowProcessor;

/**
 * This class is used by the unmarshaller in order to retrieve the headers.
 */
final class HeaderRowProcessor implements RowProcessor {
    private String[] headers;

    /**
     * Called when the processing starts, it clears the headers
     *
     * @param context Parsing context
     */
    @Override
    public void processStarted(ParsingContext context) {
        headers = null;
    }

    /**
     * Called when a row is processed, it retrieve the headers if necessary.
     *
     * @param row     Processed row
     * @param context Parsing context
     */
    @Override
    public void rowProcessed(String[] row, ParsingContext context) {
        if (headers == null) {
            headers = context.headers();
        }
    }

    /**
     * Called when the processing completes, it clears the headers.
     *
     * @param context Parsing context
     */
    @Override
    public void processEnded(ParsingContext context) {
        headers = null;
    }

    /**
     * Gets the headers.
     *
     * @return the headers
     */
    public String[] getHeaders() {
        return headers;
    }
}
