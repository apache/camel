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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.StreamCache;
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
    private final String propertyName;
    private final Configuration configuration;
    private final boolean hasSimple;
    private JsonPathAdapter adapter;
    private volatile boolean initJsonAdapter;

    @Deprecated
    public JsonPathEngine(String expression) {
        this(expression, false, false, true, null, null, null, null);
    }

    public JsonPathEngine(String expression, boolean writeAsString, boolean suppressExceptions, boolean allowSimple,
                          String headerName, String propertyName, Option[] options, CamelContext context) {
        this.expression = expression;
        this.writeAsString = writeAsString;
        this.headerName = headerName;
        this.propertyName = propertyName;

        Configuration.ConfigurationBuilder builder = Configuration.builder();
        if (options != null) {
            builder.options(options);
        }
        // Use custom ObjectMapper if provided (CAMEL-17956)
        ObjectMapper objectMapper = findRegisteredMapper(context);
        if (objectMapper != null) {
            builder.jsonProvider(new JacksonJsonProvider(objectMapper));
            builder.mappingProvider(new JacksonMappingProvider(objectMapper));
        } else {
            builder.jsonProvider(new JacksonJsonProvider());
            builder.mappingProvider(new JacksonMappingProvider());
        }

        if (suppressExceptions) {
            builder.options(SUPPRESS_EXCEPTIONS);
        }
        this.configuration = builder.build();

        boolean simpleInUse = false;
        if (allowSimple) {
            // is simple language embedded
            Matcher matcher = SIMPLE_PATTERN.matcher(expression);
            if (matcher.find()) {
                simpleInUse = true;
            }
        }
        this.hasSimple = simpleInUse;
    }

    private ObjectMapper findRegisteredMapper(CamelContext context) {
        if (context != null) {
            return context.getRegistry().findSingleByType(ObjectMapper.class);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Object read(Exchange exchange) throws Exception {
        Object answer;
        if (hasSimple) {
            // need to compile every time
            Expression exp = exchange.getContext().resolveLanguage("simple").createExpression(expression);
            String text = exp.evaluate(exchange, String.class);
            LOG.debug("Compiled dynamic JsonPath: {}", text);
            answer = doRead(text, exchange);
        } else {
            answer = doRead(expression, exchange);
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
                Iterable<Object> it = (Iterable<Object>) answer;
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
                Map<Object, Object> map = (Map<Object, Object>) answer;
                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                    Object value = entry.getValue();
                    if (adapter != null) {
                        String json = adapter.writeAsString(value, exchange);
                        if (json != null) {
                            map.put(entry.getKey(), json);
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

    private Object getPayload(Exchange exchange) {
        Object payload = null;
        if (headerName == null && propertyName == null) {
            payload = exchange.getIn().getBody();
        } else {
            if (headerName != null) {
                payload = exchange.getIn().getHeader(headerName);
            }
            if (payload == null && propertyName != null) {
                payload = exchange.getProperty(propertyName);
            }
        }
        return payload;
    }

    private Object doRead(String path, Exchange exchange) throws IOException, CamelExchangeException {
        final Object json = getPayload(exchange);

        if (json instanceof InputStream) {
            return readWithInputStream(path, exchange);
        } else if (json instanceof GenericFile) {
            LOG.trace("JSonPath: {} is read as generic file: {}", path, json);
            GenericFile<?> genericFile = (GenericFile<?>) json;
            if (genericFile.getCharset() != null) {
                // special treatment for generic file with charset
                InputStream inputStream = new FileInputStream((File) genericFile.getFile());
                return JsonPath.using(configuration).parse(inputStream, genericFile.getCharset()).read(path);
            }
        }

        if (json instanceof String) {
            LOG.trace("JSonPath: {} is read as String: {}", path, json);
            String str = (String) json;
            return JsonPath.using(configuration).parse(str).read(path);
        } else if (json instanceof Map) {
            LOG.trace("JSonPath: {} is read as Map: {}", path, json);
            Map map = (Map) json;
            return JsonPath.using(configuration).parse(map).read(path);
        } else if (json instanceof List) {
            LOG.trace("JSonPath: {} is read as List: {}", path, json);
            List list = (List) json;
            return JsonPath.using(configuration).parse(list).read(path);
        } else {
            //try to auto convert into inputStream
            Object answer = readWithInputStream(path, exchange);
            if (answer == null) {
                // fallback and attempt an adapter which can read the message body/header
                answer = readWithAdapter(path, exchange);
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
            throw new CamelExchangeException("Cannot read message header " + headerName + " as supported JSON value", exchange);
        } else {
            throw new CamelExchangeException("Cannot read message body as supported JSON value", exchange);
        }
    }

    private Object readWithInputStream(String path, Exchange exchange) throws IOException {
        Object json = headerName != null ? exchange.getIn().getHeader(headerName) : exchange.getIn().getBody();
        LOG.trace("JSonPath: {} is read as InputStream: {}", path, json);

        InputStream is = exchange.getContext().getTypeConverter().tryConvertTo(InputStream.class, exchange, json);

        if (json instanceof StreamCache) {
            ((StreamCache) json).reset();
        }

        if (is != null) {
            String jsonEncoding = exchange.getIn().getHeader(JsonPathConstants.HEADER_JSON_ENCODING, String.class);
            if (jsonEncoding != null) {
                // json encoding specified in header
                return JsonPath.using(configuration).parse(is, jsonEncoding).read(path);
            } else {
                // No json encoding specified --> assume json encoding is unicode and determine the specific unicode encoding according to RFC-4627.
                // This is a temporary solution, it can be removed as soon as jsonpath offers the encoding detection
                JsonStream jsonStream = new JsonStream(is);
                return JsonPath.using(configuration).parse(jsonStream, jsonStream.getEncoding().name()).read(path);
            }
        }

        return null;
    }

    private Object readWithAdapter(String path, Exchange exchange) {
        Object json = headerName != null ? exchange.getIn().getHeader(headerName) : exchange.getIn().getBody();
        LOG.trace("JSonPath: {} is read with adapter: {}", path, json);

        doInitAdapter(exchange);

        if (adapter != null) {
            LOG.trace("Attempting to use JacksonJsonAdapter: {}", adapter);
            Map map = adapter.readValue(json, exchange);

            if (json instanceof StreamCache) {
                ((StreamCache) json).reset();
            }

            if (map != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("JacksonJsonAdapter converted object from: {} to: java.util.Map",
                            ObjectHelper.classCanonicalName(json));
                }
                return JsonPath.using(configuration).parse(map).read(path);
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
            } catch (Exception e) {
                LOG.debug(
                        "Cannot load {} from classpath to enable JacksonJsonAdapter due {}. JacksonJsonAdapter is not enabled.",
                        JACKSON_JSON_ADAPTER, e.getMessage(),
                        e);
            }
            initJsonAdapter = true;
        }
    }
}
