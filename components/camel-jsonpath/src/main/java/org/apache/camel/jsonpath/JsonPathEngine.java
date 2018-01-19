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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST;
import static com.jayway.jsonpath.Option.SUPPRESS_EXCEPTIONS;

public class JsonPathEngine {

    private static final Logger LOG = LoggerFactory.getLogger(JsonPathEngine.class);

    private static final String JACKSON_JSON_ADAPTER = "org.apache.camel.jsonpath.jackson.JacksonJsonAdapter";

    private static final Pattern SIMPLE_PATTERN = Pattern.compile("\\$\\{[^\\}]+\\}", Pattern.MULTILINE);
    private final String expression;
    private final boolean writeAsString;
    private final String headerName;
    private final JsonPath path;
    private final Configuration configuration;
    private JsonPathAdapter adapter;
    private volatile boolean initJsonAdapter;

    @Deprecated
    public JsonPathEngine(String expression) {
        this(expression, false, false, true, null, null);
    }

    public JsonPathEngine(String expression, boolean writeAsString, boolean suppressExceptions, boolean allowSimple,
                          String headerName, Option[] options) {
        this.expression = expression;
        this.writeAsString = writeAsString;
        this.headerName = headerName;

        Configuration defaults = Configuration.defaultConfiguration();
        if (options != null) {
            Configuration.ConfigurationBuilder builder = Configuration.builder().jsonProvider(defaults.jsonProvider()).options(options);
            if (suppressExceptions) {
                builder.options(SUPPRESS_EXCEPTIONS);
            }
            this.configuration = builder.build();
        } else {
            Configuration.ConfigurationBuilder builder = Configuration.builder().jsonProvider(defaults.jsonProvider());
            if (suppressExceptions) {
                builder.options(SUPPRESS_EXCEPTIONS);
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

    @SuppressWarnings("unchecked")
    public Object read(Exchange exchange) throws Exception {
        Object answer;
        if (path == null) {
            Expression exp = exchange.getContext().resolveLanguage("simple").createExpression(expression);
            String text = exp.evaluate(exchange, String.class);
            JsonPath path = JsonPath.compile(text);
            LOG.debug("Compiled dynamic JsonPath: {}", expression);
            answer = doRead(path, exchange);
        } else {
            answer = doRead(path, exchange);
        }

        if (writeAsString) {

            if (!initJsonAdapter) {
                doInitAdapter(exchange);
            }

            if (adapter == null) {
                LOG.debug("Cannot writeAsString as adapter cannot be initialized");
                // return as-is as there is no adapter
                return answer;
            }

            // write each row as a string but keep it as a list/iterable
            if (answer instanceof Iterable) {
                List<String> list = new ArrayList<>();
                Iterable it = (Iterable) answer;
                for (Object o : it) {
                    if (adapter != null) {
                        String json = adapter.writeAsString(o, exchange);
                        if (json != null) {
                            list.add(json);
                        }
                    }
                }
                return list;
            } else if (answer instanceof Map) {
                Map map = (Map) answer;
                for (Object key : map.keySet()) {
                    Object value = map.get(key);
                    if (adapter != null) {
                        String json = adapter.writeAsString(value, exchange);
                        if (json != null) {
                            map.put(key, json);
                        }
                    }
                }
                return map;
            } else {
                String json = adapter.writeAsString(answer, exchange);
                if (json != null) {
                    return json;
                }
            }
        }

        return answer;
    }

    private Object doRead(JsonPath path, Exchange exchange) throws IOException, CamelExchangeException {
        Object json = headerName != null ? exchange.getIn().getHeader(headerName) : exchange.getIn().getBody();

        if (json instanceof InputStream) {
            return readWithInputStream(path, exchange);
        } else if (json instanceof GenericFile) {
            LOG.trace("JSonPath: {} is read as generic file: {}", path, json);
            GenericFile<?> genericFile = (GenericFile<?>) json;
            if (genericFile.getCharset() != null) {
                // special treatment for generic file with charset
                InputStream inputStream = new FileInputStream((File) genericFile.getFile());
                return path.read(inputStream, genericFile.getCharset(), configuration);
            }
        }

        if (json instanceof String) {
            LOG.trace("JSonPath: {} is read as String: {}", path, json);
            String str = (String) json;
            return path.read(str, configuration);
        } else if (json instanceof Map) {
            LOG.trace("JSonPath: {} is read as Map: {}", path, json);
            Map map = (Map) json;
            return path.read(map, configuration);
        } else if (json instanceof List) {
            LOG.trace("JSonPath: {} is read as List: {}", path, json);
            List list = (List) json;
            return path.read(list, configuration);
        } else {
            // can we find an adapter which can read the message body/header
            Object answer = readWithAdapter(path, exchange);
            if (answer == null) {
                // fallback and attempt input stream for any other types
                answer = readWithInputStream(path, exchange);
            }
            if (answer != null) {
                return answer;
            }
        }

        // is json path configured to suppress exceptions
        if (configuration.getOptions().contains(SUPPRESS_EXCEPTIONS)) {
            if (configuration.getOptions().contains(ALWAYS_RETURN_LIST)) {
                return Collections.emptyList();
            } else {
                return null;
            }
        }

        // okay it was not then lets throw a failure
        if (headerName != null) {
            throw new CamelExchangeException("Cannot read message header " + headerName + " as supported JSon value", exchange);
        } else {
            throw new CamelExchangeException("Cannot read message body as supported JSon value", exchange);
        }
    }

    private Object readWithInputStream(JsonPath path, Exchange exchange) throws IOException {
        Object json = headerName != null ? exchange.getIn().getHeader(headerName) : exchange.getIn().getBody();
        LOG.trace("JSonPath: {} is read as InputStream: {}", path, json);

        InputStream is = exchange.getContext().getTypeConverter().tryConvertTo(InputStream.class, exchange, json);
        if (is != null) {
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

        return null;
    }

    private Object readWithAdapter(JsonPath path, Exchange exchange) {
        Object json = headerName != null ? exchange.getIn().getHeader(headerName) : exchange.getIn().getBody();
        LOG.trace("JSonPath: {} is read with adapter: {}", path, json);

        doInitAdapter(exchange);

        if (adapter != null) {
            LOG.trace("Attempting to use JacksonJsonAdapter: {}", adapter);
            Map map = adapter.readValue(json, exchange);
            if (map != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("JacksonJsonAdapter converted object from: {} to: java.util.Map", ObjectHelper.classCanonicalName(json));
                }
                return path.read(map, configuration);
            }
        }

        return null;
    }

    private void doInitAdapter(Exchange exchange) {
        if (!initJsonAdapter) {
            try {
                // need to load this adapter dynamically as its optional
                LOG.debug("Attempting to enable JacksonJsonAdapter by resolving: {} from classpath", JACKSON_JSON_ADAPTER);
                Class<?> clazz = exchange.getContext().getClassResolver().resolveClass(JACKSON_JSON_ADAPTER);
                if (clazz != null) {
                    Object obj = exchange.getContext().getInjector().newInstance(clazz);
                    if (obj instanceof JsonPathAdapter) {
                        adapter = (JsonPathAdapter) obj;
                        adapter.init(exchange.getContext());
                        LOG.debug("JacksonJsonAdapter found on classpath and enabled for camel-jsonpath: {}", adapter);
                    }
                }
            } catch (Throwable e) {
                // ignore
            }
            initJsonAdapter = true;
        }
    }
}
