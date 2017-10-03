/**
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
package org.apache.camel.component.everit.jsonschema;

import java.io.InputStream;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.IOHelper;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A processor which validates the JSON of the inbound message body
 * against some JSON schema
 */
public class JsonValidatingProcessor implements AsyncProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(JsonValidatingProcessor.class);
    private JsonSchemaReader schemaReader;
    private JsonValidatorErrorHandler errorHandler = new DefaultJsonValidationErrorHandler();
    private boolean failOnNullBody = true;
    private boolean failOnNullHeader = true;
    private String headerName;

    public JsonValidatingProcessor() {
        
    }

    public JsonValidatingProcessor(JsonSchemaReader schemaReader) {
        this.schemaReader = schemaReader;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            doProcess(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    protected void doProcess(Exchange exchange) throws Exception {
        Object jsonPayload = null;
        InputStream is = null;
        Schema schema = null;
        try {
            is = getContentToValidate(exchange, InputStream.class);
            if (shouldUseHeader()) {
                if (is == null && isFailOnNullHeader()) {
                    throw new NoJsonHeaderValidationException(exchange, headerName);
                }
            } else {
                if (is == null && isFailOnNullBody()) {
                    throw new NoJsonBodyValidationException(exchange);
                }
            }
            if (is != null) {
                schema = this.schemaReader.getSchema();
                if (schema instanceof ObjectSchema) {
                    jsonPayload = new JSONObject(new JSONTokener(is));
                } else { 
                    jsonPayload = new JSONArray(new JSONTokener(is));
                }
                // throws a ValidationException if this object is invalid
                schema.validate(jsonPayload); 
                LOG.debug("JSON is valid");
            }
        } catch (ValidationException e) {
            this.errorHandler.handleErrors(exchange, schema, e);
        } catch (JSONException e) {
            this.errorHandler.handleErrors(exchange, schema, e);
        } finally {
            IOHelper.close(is);
        }
    } 
    
    private <T> T getContentToValidate(Exchange exchange, Class<T> clazz) {
        if (shouldUseHeader()) {
            return exchange.getIn().getHeader(headerName, clazz);
        } else {
            return exchange.getIn().getBody(clazz);
        }
    }

    private boolean shouldUseHeader() {
        return headerName != null;
    }

    // Properties
    // -----------------------------------------------------------------------


    public JsonValidatorErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(JsonValidatorErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public boolean isFailOnNullBody() {
        return failOnNullBody;
    }

    public void setFailOnNullBody(boolean failOnNullBody) {
        this.failOnNullBody = failOnNullBody;
    }

    public boolean isFailOnNullHeader() {
        return failOnNullHeader;
    }

    public void setFailOnNullHeader(boolean failOnNullHeader) {
        this.failOnNullHeader = failOnNullHeader;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
}
