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
import java.io.InputStream;
import java.io.Reader;

import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.stax.StAXSource;

import org.apache.camel.Exchange;
import org.apache.camel.ExpectedBodyTypeException;
import org.apache.camel.Expression;
import org.apache.camel.component.xslt.SourceHandlerFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.core.json.JsonFactory;

/**
 * Handler for json sources
 */
public class JsonSourceHandlerFactoryImpl implements SourceHandlerFactory {

    private final JsonFactory jsonFactory;
    private boolean isFailOnNullBody = true;

    /**
     * Creates a new instance
     *
     * @param jsonFactory the jsonFactory to use to read the json document
     */
    public JsonSourceHandlerFactoryImpl(JsonFactory jsonFactory) {
        this.jsonFactory = jsonFactory;
    }

    /**
     * Returns if we fail when the body is null
     */
    public boolean isFailOnNullBody() {
        return isFailOnNullBody;
    }

    /**
     * Set if we should fail when the body is null
     */
    public void setFailOnNullBody(boolean failOnNullBody) {
        isFailOnNullBody = failOnNullBody;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Source getSource(Exchange exchange, Expression source) throws Exception {
        Object body = source != null ? source.evaluate(exchange, Object.class) : exchange.getMessage().getBody();

        JsonParser jsonParser = null;
        if (body instanceof File) {
            try {
                jsonParser = jsonFactory.createParser(ObjectReadContext.empty(), (File) body);
            } catch (JacksonIOException e) {
                // a file that cannot be read stays an IOException
                throw e.getCause();
            }
        } else if (body instanceof InputStream) {
            jsonParser = jsonFactory.createParser(ObjectReadContext.empty(), (InputStream) body);
        } else if (body instanceof Reader) {
            jsonParser = jsonFactory.createParser(ObjectReadContext.empty(), (Reader) body);
        } else if (body instanceof byte[]) {
            jsonParser = jsonFactory.createParser(ObjectReadContext.empty(), (byte[]) body);
        } else if (body instanceof String) {
            jsonParser = jsonFactory.createParser(ObjectReadContext.empty(), (String) body);
        }

        if (jsonParser == null) {
            final String bodyAsString = exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, body);
            if (bodyAsString != null) {
                jsonParser = jsonFactory.createParser(ObjectReadContext.empty(), bodyAsString);
            }
        }

        if (jsonParser == null) {
            if (isFailOnNullBody()) {
                throw new ExpectedBodyTypeException(exchange, Source.class);
            } else {
                jsonParser = jsonFactory.createParser(ObjectReadContext.empty(), "{}");
            }
        }

        final XMLStreamReader xmlStreamReader = new JsonXmlStreamReader(jsonParser);
        return new StAXSource(xmlStreamReader);
    }
}
