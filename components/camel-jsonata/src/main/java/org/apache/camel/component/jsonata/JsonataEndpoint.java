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
package org.apache.camel.component.jsonata;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.api.jsonata4java.expressions.Expressions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;

/**
 * JSON to JSON transformation using JSONATA.
 */
@UriEndpoint(firstVersion = "3.5.0", scheme = "jsonata", title = "JSONATA", syntax = "jsonata:resourceUri", producerOnly = true,
             category = { Category.TRANSFORMATION })
public class JsonataEndpoint extends ResourceEndpoint {

    private Expressions expressions;

    @UriParam(defaultValue = "Jackson")
    private JsonataInputOutputType outputType;

    @UriParam(defaultValue = "Jackson")
    private JsonataInputOutputType inputType;

    public JsonataEndpoint() {
    }

    public JsonataEndpoint(String uri, JsonataComponent component, String resourceUri) {
        super(uri, component, resourceUri);
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return "Jsonata:" + getResourceUri();
    }

    public JsonataInputOutputType getOutputType() {
        return outputType;
    }

    /**
     * Specifies if the output should be Jackson JsonNode or a JSON String.
     */
    public void setOutputType(JsonataInputOutputType outputType) {
        this.outputType = outputType;
    }

    public JsonataInputOutputType getInputType() {
        return inputType;
    }

    /**
     * Specifies if the output should be Jackson JsonNode or a JSON String.
     */
    public void setInputType(JsonataInputOutputType inputType) {
        this.inputType = inputType;
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(path, "resourceUri");

        JsonNode input;
        ObjectMapper mapper = new ObjectMapper();
        if (getInputType() == JsonataInputOutputType.JsonString) {
            input = mapper.readTree(exchange.getIn().getBody(InputStream.class));
        } else {
            input = (JsonNode) exchange.getIn().getBody();
        }

        JsonNode output = null;
        if (expressions == null) {
            String spec = new BufferedReader(
                    new InputStreamReader(getResourceAsInputStream(), StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining("\n"));
            expressions = Expressions.parse(spec);
        }
        output = expressions.evaluate(input);

        // now lets output the results to the exchange 
        Message out = exchange.getOut(); // getOut() is depricated
        if (getOutputType() == JsonataInputOutputType.JsonString) {
            out.setBody(output.toString());
        } else {
            out.setBody(output);
        }
        out.setHeaders(exchange.getIn().getHeaders());
    }
}
