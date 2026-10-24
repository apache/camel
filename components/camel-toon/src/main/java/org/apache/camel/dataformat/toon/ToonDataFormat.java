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
package org.apache.camel.dataformat.toon;

import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.Locale;

import dev.toonformat.jtoon.DecodeOptions;
import dev.toonformat.jtoon.Delimiter;
import dev.toonformat.jtoon.EncodeOptions;
import dev.toonformat.jtoon.JToon;
import dev.toonformat.jtoon.KeyFolding;
import dev.toonformat.jtoon.PathExpansion;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatContentTypeHeader;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;

/**
 * Marshal JSON-compatible Java values to TOON (Token-Oriented Object Notation) and unmarshal TOON back to Java objects
 * using the <a href="https://github.com/toon-format/toon-java">JToon</a> library.
 */
@Dataformat("toon")
@Metadata(firstVersion = "4.23.0", title = "TOON")
@BodyTypes(marshalIn = @BodyType(value = {
        Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.VALUE, Carrier.MAP,
        Carrier.LIST, Carrier.POJO },
                                 description = "A Map, a List, a POJO or a value to write as TOON. A String, bytes, a stream or"
                                               + " a file must be JSON text, read in the charset of the exchange"),
           marshalOut = @BodyType(value = Carrier.STREAM, format = "text",
                                  description = "The TOON in the stream cache, or a byte array when stream caching is off"),
           unmarshalIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }, format = "text"),
           unmarshalOut = @BodyType(value = { Carrier.MAP, Carrier.LIST },
                                    description = "A Map (LinkedHashMap) for an object, a List for an array; a document that is"
                                                  + " a single value gives that value"))
public class ToonDataFormat extends ServiceSupport
        implements DataFormat, DataFormatName, DataFormatContentTypeHeader, CamelContextAware {

    static final String CONTENT_TYPE = "text/toon";

    private CamelContext camelContext;
    @Metadata(description = "Number of spaces per indentation level.", defaultValue = "2", javaType = "java.lang.Integer")
    private int indent = EncodeOptions.DEFAULT.indent();
    @Metadata(description = "Delimiter used for tabular array rows and inline primitive arrays.",
              defaultValue = "COMMA", enums = "COMMA,TAB,PIPE")
    private String delimiter = Delimiter.COMMA.name();
    @Metadata(description = "Whether to enable strict validation when unmarshalling TOON. When false, the decoder applies the non-strict"
                            + " recoveries of the TOON specification, such as accepting an array whose length differs from the"
                            + " declared length; other malformed TOON still fails.",
              defaultValue = "true", javaType = "java.lang.Boolean")
    private boolean strict = DecodeOptions.DEFAULT.strict();
    @Metadata(description = "Whether the data format should set the Content-Type header to text/toon when marshalling.",
              defaultValue = "true", javaType = "java.lang.Boolean")
    private boolean contentTypeHeader = true;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getDataFormatName() {
        return "toon";
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        EncodeOptions options = encodeOptions();
        String toon;
        if (graph instanceof String json) {
            // String bodies are JSON documents (JSON-to-TOON), not TOON string scalars.
            // JToon.encodeJson rejects non-JSON text such as "hello world".
            toon = JToon.encodeJson(json, options);
        } else if (graph instanceof byte[] || graph instanceof InputStream || graph instanceof File
                || graph instanceof WrappedFile) {
            // bytes, a stream or a file hold JSON text as a String does (read in the charset of the exchange),
            // which JToon.encode would write as an array of numbers, nothing or the path of the file
            String json = exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, graph);
            toon = JToon.encodeJson(json, options);
        } else {
            toon = JToon.encode(graph, options);
        }

        try (OutputStreamWriter osw = new OutputStreamWriter(stream, ExchangeHelper.getCharsetName(exchange));
             BufferedWriter writer = IOHelper.buffered(osw)) {
            writer.write(toon);
        }

        if (contentTypeHeader) {
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, CONTENT_TYPE);
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return unmarshal(exchange, (Object) stream);
    }

    @Override
    public Object unmarshal(Exchange exchange, Object body) throws Exception {
        return JToon.decode(toToonString(exchange, body), decodeOptions());
    }

    private String toToonString(Exchange exchange, Object body) throws Exception {
        if (body instanceof String s) {
            return s;
        } else if (body instanceof Reader r) {
            return IOHelper.toString(r);
        } else {
            InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body);
            Reader r = new InputStreamReader(is, ExchangeHelper.getCharsetName(exchange));
            return IOHelper.toString(r);
        }
    }

    private EncodeOptions encodeOptions() {
        return new EncodeOptions(indent, resolveDelimiter(), KeyFolding.OFF, Integer.MAX_VALUE);
    }

    private DecodeOptions decodeOptions() {
        return new DecodeOptions(
                indent, resolveDelimiter(), strict, PathExpansion.OFF, DecodeOptions.MAX_ALLOWED_DEPTH,
                DecodeOptions.DEFAULT_MAX_ARRAY_SIZE, DecodeOptions.DEFAULT_MAX_STRING_LENGTH);
    }

    private Delimiter resolveDelimiter() {
        if (delimiter == null || delimiter.isBlank()) {
            return Delimiter.COMMA;
        }
        return Delimiter.valueOf(delimiter.trim().toUpperCase(Locale.ROOT));
    }

    public int getIndent() {
        return indent;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public boolean isStrict() {
        return strict;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public boolean isContentTypeHeader() {
        return contentTypeHeader;
    }

    @Override
    public void setContentTypeHeader(boolean contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }
}
