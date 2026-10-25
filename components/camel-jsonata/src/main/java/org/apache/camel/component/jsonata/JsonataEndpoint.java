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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Collectors;

import com.dashjoin.jsonata.Jsonata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;

import static com.dashjoin.jsonata.Jsonata.jsonata;

/**
 * Transforms JSON payload using JSONata transformation.
 */
@UriEndpoint(firstVersion = "3.5.0", scheme = "jsonata", title = "JSONata", syntax = "jsonata:resourceUri",
             producerOnly = true, remote = false,
             category = { Category.TRANSFORMATION })
@BodyTypes(producerIn = @BodyType(value = {
        Carrier.MAP, Carrier.LIST, Carrier.POJO, Carrier.TEXT, Carrier.BYTES,
        Carrier.STREAM, Carrier.FILE }, format = "json",
                                  byOption = @BodyType.When(option = "inputType", value = "JsonString",
                                                            carriers = {
                                                                    Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                    Carrier.FILE }),
                                  description = "JSON data: a Map, a List, a JsonNode or another object Jackson converts, or"
                                                + " JSON as text, bytes, a stream or a file, which is parsed. Text that does"
                                                + " not start with { or [ is read as one JSON string. With"
                                                + " inputType=JsonString the body is always parsed as JSON"),
           producerOut = @BodyType(value = Carrier.POJO, javaType = "com.fasterxml.jackson.databind.JsonNode",
                                   format = "json",
                                   byOption = @BodyType.When(option = "outputType", value = "JsonString",
                                                             carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                   description = "The result as a Jackson JsonNode, or as JSON text with"
                                                 + " outputType=JsonString"))
public class JsonataEndpoint extends ResourceEndpoint {

    private final ObjectMapper mapper = new ObjectMapper();

    @UriParam(defaultValue = "Jackson")
    private JsonataInputOutputType outputType;
    @UriParam(defaultValue = "Jackson")
    private JsonataInputOutputType inputType;
    @UriParam(label = "advanced", description = "To configure the Jsonata frame binding. Allows custom functions to be added.")
    private JsonataFrameBinding frameBinding;
    @UriParam
    private boolean allowTemplateFromHeader;
    @UriParam
    private boolean prettyPrint;

    public JsonataEndpoint() {
    }

    public JsonataEndpoint(String uri, JsonataComponent component, String resourceUri, JsonataFrameBinding frameBinding) {
        super(uri, component, resourceUri);
        this.frameBinding = frameBinding;
    }

    @Override
    public boolean isRemote() {
        return false;
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
     * Specifies if the input should be Jackson JsonNode or a JSON String. With Jackson, a body that is JSON text (its
     * first non-whitespace character is { or [), bytes, a stream or a file is parsed as JSON, other text is a JSON
     * string value, and any other body, such as a Map or a List, is converted by Jackson. With JsonString, the body is
     * always parsed as JSON.
     */
    public void setInputType(JsonataInputOutputType inputType) {
        this.inputType = inputType;
    }

    /**
     * Specifies the custom framebinding.
     */
    public void setFrameBinding(JsonataFrameBinding frameBinding) {
        this.frameBinding = frameBinding;
    }

    public JsonataFrameBinding getFrameBinding() {
        return frameBinding;
    }

    public boolean isAllowTemplateFromHeader() {
        return allowTemplateFromHeader;
    }

    /**
     * Whether to allow to use resource template from header or not (default false).
     *
     * Enabling this allows to specify dynamic templates via message header. However this can be seen as a potential
     * security vulnerability if the header is coming from a malicious user, so use this with care.
     */
    public void setAllowTemplateFromHeader(boolean allowTemplateFromHeader) {
        this.allowTemplateFromHeader = allowTemplateFromHeader;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    /**
     * Whether to pretty print JSon output when using string as output type.
     */
    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(path, "resourceUri");

        Object input;
        if (getInputType() == JsonataInputOutputType.JsonString) {
            InputStream inputStream = exchange.getIn().getBody(InputStream.class);
            input = mapper.readValue(inputStream, Object.class);
        } else {
            input = jacksonInput(exchange);
        }

        InputStream is = getResourceAsInputStream();
        if (allowTemplateFromHeader) {
            String newResourceUri = exchange.getIn().getHeader(JsonataConstants.JSONATA_RESOURCE_URI, String.class);
            if (newResourceUri != null) {
                exchange.getIn().removeHeader(JsonataConstants.JSONATA_RESOURCE_URI);
                if (ResourceHelper.hasScheme(newResourceUri)) {
                    is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), newResourceUri);
                } else {
                    is = new ByteArrayInputStream(newResourceUri.getBytes());
                }
            }
        }
        Jsonata expression;
        try (InputStreamReader inputStreamReader
                = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader);) {
            String spec = bufferedReader
                    .lines()
                    .collect(Collectors.joining("\n"));
            expression = jsonata(spec);
        }

        Jsonata.Frame frame = expression.createFrame();
        if (frameBinding != null) {
            frameBinding.bindToFrame(frame);
        }
        Object outputLib = expression.evaluate(input, frame);
        String bodyAsString = prettyPrint
                ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(outputLib) : mapper.writeValueAsString(outputLib);

        // now lets output the results to the exchange
        final Object output;
        if (getOutputType() == JsonataInputOutputType.JsonString) {
            output = bodyAsString;
        } else {
            output = mapper.readTree(bodyAsString);
        }
        ExchangeHelper.setInOutBodyPatternAware(exchange, output);
    }

    /**
     * The input for the Jackson input type: JSON text, bytes, a stream or a file is parsed, any other body (such as a
     * Map or a List) is converted by Jackson. Text that is not a JSON object or array is a JSON string value.
     */
    private Object jacksonInput(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (body instanceof CharSequence text) {
            return isJsonText(text) ? mapper.readValue(text.toString(), Object.class) : text.toString();
        }
        if (body instanceof byte[] || body instanceof InputStream || body instanceof StreamCache
                || body instanceof File || body instanceof Path || body instanceof WrappedFile) {
            if (ExchangeHelper.getCharsetName(exchange, false) != null) {
                // decode the text in the charset of the exchange
                return mapper.readValue(exchange.getIn().getMandatoryBody(String.class), Object.class);
            }
            // let Jackson detect the encoding
            return mapper.readValue(exchange.getIn().getMandatoryBody(InputStream.class), Object.class);
        }
        return mapper.convertValue(body, Object.class);
    }

    /**
     * Whether the text is a JSON object or array, that is whether its first non-whitespace character is { or [
     */
    private static boolean isJsonText(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isWhitespace(ch)) {
                return ch == '{' || ch == '[';
            }
        }
        return false;
    }

}
