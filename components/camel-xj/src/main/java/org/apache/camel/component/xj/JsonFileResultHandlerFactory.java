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
package org.apache.camel.component.xj;

import java.io.File;

import com.fasterxml.jackson.core.JsonFactory;
import org.apache.camel.Exchange;
import org.apache.camel.component.xslt.ResultHandler;
import org.apache.camel.component.xslt.ResultHandlerFactory;
import org.apache.camel.support.ExchangeHelper;

/**
 * A {@link JsonFileResultHandler} factory
 */
public class JsonFileResultHandlerFactory implements ResultHandlerFactory {
    private final JsonFactory jsonFactory;

    /**
     * Creates a new json to file result handler factory
     *
     * @param jsonFactory the {@link JsonFactory} to use to write the json.
     */
    public JsonFileResultHandlerFactory(JsonFactory jsonFactory) {
        this.jsonFactory = jsonFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultHandler createResult(Exchange exchange) throws Exception {
        final String fileName = ExchangeHelper.getMandatoryHeader(exchange, Exchange.XSLT_FILE_NAME, String.class);
        return new JsonFileResultHandler(jsonFactory, new File(fileName));
    }
}
