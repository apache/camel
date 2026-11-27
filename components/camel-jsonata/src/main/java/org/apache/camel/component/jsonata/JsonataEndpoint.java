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
import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static com.dashjoin.jsonata.Jsonata.jsonata;

/**
 * Transform JSON data with a JSONata expression.
 */
@UriEndpoint(firstVersion = "3.5.0", scheme = "jsonata", title = "JSONata", syntax = "jsonata:resourceUri",
             producerOnly = true, remote = false,
             category = { Category.TRANSFORMATION }, headersClass = JsonataConstants.class)
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
           producerOut = @BodyType(value = Carrier.POJO, javaType = "tools.jackson.databind.JsonNode",
                                   format = "json",
                                   byOption = @BodyType.When(option = "outputType", value = "JsonString",
                                                             carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                   description = "The result as a Jackson 3 JsonNode, or as JSON text with"
                                                 + " outputType=JsonString"))
public class JsonataEndpoint extends ResourceEndpoint {

    private static final String JACKSON2_NODE_PACKAGE = "com.fasterxml.jackson.databind.node.";

    // keeps the Jackson 2 defaults, so the input of the expression and its output do not change
    private final ObjectMapper mapper = JsonMapper.builderWithJackson2Defaults().build();

    @UriParam(defaultValue = "Jackson",
              description = "The type of the result that the producer sets as the message body: Jackson for a Jackson 3"
                            + " JsonNode, or JsonString for the JSON text.")
    private JsonataInputOutputType outputType;
    @UriParam(defaultValue = "Jackson",
              description = "How the producer reads the message body as the input of the expression. With Jackson, a body"
                            + " that is JSON text (its first non-whitespace character is { or [), bytes, a stream or a file"
                            + " is parsed as JSON, other text is a JSON string value, and any other body, such as a Map or a"
                            + " List, is converted by Jackson. With JsonString, the body is always parsed as JSON.")
    private JsonataInputOutputType inputType;
    @UriParam(label = "advanced",
              description = "A JsonataFrameBinding that the producer calls with the Jsonata.Frame of each evaluation, to bind"
                            + " custom functions and variables that the expression can use. If not set, the frameBinding"
                            + " option of the component is used.")
    private JsonataFrameBinding frameBinding;
    @UriParam(description = "Whether the producer takes the JSONata expression from the CamelJsonataResourceUri header,"
                            + " when the header is set, instead of from the resourceUri option. Enable it only when the"
                            + " header comes from a trusted source, as its value can be any expression or the URI of any"
                            + " resource that Camel can read.")
    private boolean allowTemplateFromHeader;
    @UriParam(description = "Whether to format the JSON text of the result with indentation and line breaks. It applies"
                            + " only when outputType is JsonString.")
    private boolean prettyPrint;
    // shadows the option of ResourceEndpoint, so it can be marked deprecated for jsonata only
    @UriParam(defaultValue = "false", description = "Not used by jsonata.")
    @Metadata(deprecationNote = "The jsonata component builds no context map, so this option has no effect.")
    @Deprecated(since = "4.24.0")
    private boolean allowContextMapAll;

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

    public void setOutputType(JsonataInputOutputType outputType) {
        this.outputType = outputType;
    }

    public JsonataInputOutputType getInputType() {
        return inputType;
    }

    public void setInputType(JsonataInputOutputType inputType) {
        this.inputType = inputType;
    }

    public void setFrameBinding(JsonataFrameBinding frameBinding) {
        this.frameBinding = frameBinding;
    }

    public JsonataFrameBinding getFrameBinding() {
        return frameBinding;
    }

    public boolean isAllowTemplateFromHeader() {
        return allowTemplateFromHeader;
    }

    public void setAllowTemplateFromHeader(boolean allowTemplateFromHeader) {
        this.allowTemplateFromHeader = allowTemplateFromHeader;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Not used by jsonata.
     *
     * @deprecated this option has no effect in jsonata
     */
    @Deprecated(since = "4.24.0")
    @Override
    public boolean isAllowContextMapAll() {
        return allowContextMapAll;
    }

    /**
     * Not used by jsonata. The component builds no context map, so this option has no effect.
     *
     * @deprecated this option has no effect in jsonata
     */
    @Deprecated(since = "4.24.0")
    @Override
    public void setAllowContextMapAll(boolean allowContextMapAll) {
        super.setAllowContextMapAll(allowContextMapAll);
        this.allowContextMapAll = allowContextMapAll;
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

        InputStream is = null;
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
        if (is == null) {
            // the resource of the endpoint is opened only when the header does not replace it
            is = getResourceAsInputStream();
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
        if (body != null && body.getClass().getName().startsWith(JACKSON2_NODE_PACKAGE)) {
            // Jackson 3 would convert the node as a bean; its text is its JSON
            return mapper.readValue(body.toString(), Object.class);
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
