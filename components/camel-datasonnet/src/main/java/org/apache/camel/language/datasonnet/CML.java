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
package org.apache.camel.language.datasonnet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.datasonnet.document.DefaultDocument;
import com.datasonnet.document.Document;
import com.datasonnet.document.MediaTypes;
import com.datasonnet.header.Header;
import com.datasonnet.jsonnet.Materializer;
import com.datasonnet.jsonnet.Val;
import com.datasonnet.spi.DataFormatService;
import com.datasonnet.spi.Library;
import com.datasonnet.spi.PluginException;
import org.apache.camel.Exchange;

public final class CML extends Library {
    private static final CML INSTANCE = new CML();
    private final ThreadLocal<Exchange> exchange = new ThreadLocal<>();

    private CML() {
    }

    public static CML getInstance() {
        return INSTANCE;
    }

    public ThreadLocal<Exchange> getExchange() {
        return exchange;
    }

    @Override
    public String namespace() {
        return "cml";
    }

    @Override
    public Set<String> libsonnets() {
        return Collections.emptySet();
    }

    @Override
    public Map<String, Val.Func> functions(DataFormatService dataFormats, Header header) {
        Map<String, Val.Func> answer = new HashMap<>();
        answer.put("properties", makeSimpleFunc(
                Collections.singletonList("key"), //parameters list
                params -> properties(params.get(0))));
        answer.put("header", makeSimpleFunc(
                Collections.singletonList("key"), //parameters list
                params -> header(params.get(0), dataFormats)));
        answer.put("exchangeProperty", makeSimpleFunc(
                Collections.singletonList("key"), //parameters list
                params -> exchangeProperty(params.get(0), dataFormats)));

        return answer;
    }

    public Map<String, Val.Obj> modules(DataFormatService dataFormats, Header header) {
        return Collections.emptyMap();
    }

    private Val properties(Val key) {
        if (key instanceof Val.Str) {
            return new Val.Str(exchange.get().getContext().resolvePropertyPlaceholders("{{" + ((Val.Str) key).value() + "}}"));
        }
        throw new IllegalArgumentException("Expected String got: " + key.prettyName());
    }

    private Val header(Val key, DataFormatService dataformats) {
        if (key instanceof Val.Str) {
            return valFrom(exchange.get().getMessage().getHeader(((Val.Str) key).value()), dataformats);
        }
        throw new IllegalArgumentException("Expected String got: " + key.prettyName());
    }

    private Val exchangeProperty(Val key, DataFormatService dataformats) {
        if (key instanceof Val.Str) {
            return valFrom(exchange.get().getProperty(((Val.Str) key).value()), dataformats);
        }
        throw new IllegalArgumentException("Expected String got: " + key.prettyName());
    }

    private Val valFrom(Object obj, DataFormatService dataformats) {
        Document doc;
        if (obj instanceof Document) {
            doc = (Document) obj;
        } else {
            doc = new DefaultDocument(obj, MediaTypes.APPLICATION_JAVA);
        }

        try {
            return Materializer.reverse(dataformats.mandatoryRead(doc));
        } catch (PluginException e) {
            throw new IllegalStateException(e);
        }
    }
}
