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
package org.apache.camel.jsonpath;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Configuration.Defaults;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.internal.DefaultsImpl;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.file.GenericFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonPathEngine {

    private static final Logger LOG = LoggerFactory.getLogger(JsonPathEngine.class);

    private static final Pattern SIMPLE_PATTERN = Pattern.compile("\\$\\{[^\\}]+\\}", Pattern.MULTILINE);
    private final String expression;
    private final JsonPath path;
    private final Configuration configuration;

    public JsonPathEngine(String expression) {
        this(expression, false, true, null);
    }

    public JsonPathEngine(String expression, boolean suppressExceptions, boolean allowSimple, Option[] options) {
        this.expression = expression;

        Defaults defaults = DefaultsImpl.INSTANCE;
        if (options != null) {
            Configuration.ConfigurationBuilder builder = Configuration.builder().jsonProvider(defaults.jsonProvider()).options(options);
            if (suppressExceptions) {
                builder.options(Option.SUPPRESS_EXCEPTIONS);
            }
            this.configuration = builder.build();
        } else {
            Configuration.ConfigurationBuilder builder = Configuration.builder().jsonProvider(defaults.jsonProvider());
            if (suppressExceptions) {
                builder.options(Option.SUPPRESS_EXCEPTIONS);
            }
            this.configuration = builder.build();
        }

        boolean hasSimple = false;
        if (allowSimple) {
            // is simple language embedded
            Matcher matcher = SIMPLE_PATTERN.matcher(expression);
            if (matcher.find()) {
                hasSimple = true;
            }
        }
        if (hasSimple) {
            this.path = null;
        } else {
            this.path = JsonPath.compile(expression);
            LOG.debug("Compiled static JsonPath: {}", expression);
        }
    }

    public Object read(Exchange exchange) throws IOException, InvalidPayloadException {
        if (path == null) {
            Expression exp = exchange.getContext().resolveLanguage("simple").createExpression(expression);
            String text = exp.evaluate(exchange, String.class);
            JsonPath path = JsonPath.compile(text);
            LOG.debug("Compiled dynamic JsonPath: {}", expression);
            return doRead(path, exchange);
        } else {
            return doRead(path, exchange);
        }
    }

    private Object doRead(JsonPath path, Exchange exchange) throws IOException, InvalidPayloadException {
        Object json = exchange.getIn().getBody();

        if (json instanceof GenericFile) {
            GenericFile<?> genericFile = (GenericFile<?>) json;
            if (genericFile.getCharset() != null) {
                // special treatment for generic file with charset
                InputStream inputStream = new FileInputStream((File) genericFile.getFile());
                return path.read(inputStream, genericFile.getCharset(), configuration);
            }
        }

        if (json instanceof String) {
            String str = (String) json;
            return path.read(str, configuration);
        } else {
            InputStream is = exchange.getIn().getMandatoryBody(InputStream.class);
            String jsonEncoding = exchange.getIn().getHeader(JsonPathConstants.HEADER_JSON_ENCODING, String.class);
            if (jsonEncoding != null) {
                // json encoding specified in header
                return path.read(is, jsonEncoding, configuration);
            } else {
                // No json encoding specified --> assume json encoding is unicode and determine the specific unicode encoding according to RFC-4627.
                // This is a temporary solution, it can be removed as soon as jsonpath offers the encoding detection
                JsonStream jsonStream = new JsonStream(is);
                return path.read(jsonStream, jsonStream.getEncoding().name(), configuration);
            }
        }
    }
}
