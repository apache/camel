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
package org.apache.camel.component.grok;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.util.*;
import java.util.stream.Stream;

import io.krakens.grok.api.Grok;
import io.krakens.grok.api.GrokCompiler;
import io.krakens.grok.api.Match;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

@Dataformat("grok")
public class GrokDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {
    private CamelContext camelContext;

    private Grok grok;

    private boolean allowMultipleMatchesPerLine = true;
    private Set<GrokPattern> customPatterns = new HashSet<>();
    private boolean flattened;
    private boolean namedOnly;
    private String pattern;

    public GrokDataFormat(String pattern) {
        this.pattern = pattern;
    }

    public GrokDataFormat() {
    }

    public GrokDataFormat registerPatternDefinition(GrokPattern pattern) {
        this.customPatterns.add(pattern);
        this.refreshGrok();
        return this;
    }

    /**
     * @param name    : Pattern Name
     * @param pattern : Regular expression Or Grok pattern
     */
    public GrokDataFormat registerPatternDefinition(String name, String pattern) {
        return registerPatternDefinition(new GrokPattern(name, pattern));
    }

    public GrokDataFormat setPattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    /**
     * Sets the flattened mode flag
     *
     * @param flattened If true, conversion throws exception for conficting named matches.
     */
    public GrokDataFormat setFlattened(boolean flattened) {
        this.flattened = flattened;
        return this;
    }

    public GrokDataFormat setAllowMultipleMatchesPerLine(boolean allowMultipleMatchesPerLine) {
        this.allowMultipleMatchesPerLine = allowMultipleMatchesPerLine;
        return this;
    }

    /**
     * Whether to capture named expressions only or not (i.e. %{IP:ip} but not ${IP})
     */
    public GrokDataFormat setNamedOnly(boolean namedOnly) {
        this.namedOnly = namedOnly;
        return this;
    }

    private void refreshGrok() {
        ObjectHelper.notNull(pattern, "pattern");

        GrokCompiler grokCompiler = GrokCompiler.newInstance();
        grokCompiler.registerDefaultPatterns();
        for (GrokPattern pattern : customPatterns) {
            grokCompiler.register(pattern.getName(), pattern.getPattern());
        }
        grok = grokCompiler.compile(pattern, namedOnly);
    }

    @Override
    public String getDataFormatName() {
        return "grok";
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        throw new UnsupportedOperationException("GrokDataFormat does not support marshalling. Use unmarshal instead.");
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return unmarshal(exchange, (Object) stream);
    }

    @Override
    public Object unmarshal(Exchange exchange, Object body) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();

        Reader reader = null;
        if (body instanceof String s) {
            reader = new StringReader(s);
        } else if (body instanceof Reader r) {
            reader = r;
        } else {
            // fallback to input stream
            InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body);
            reader = new InputStreamReader(is, ExchangeHelper.getCharsetName(exchange));
        }

        try (Stream<String> lines = new BufferedReader(reader).lines()) {
            lines.forEachOrdered(line -> processLine(line, result));
        }

        if (result.isEmpty()) {
            return null;
        }
        if (result.size() == 1) {
            return result.get(0);
        }

        return result;
    }

    private void processLine(String line, List<Map<String, Object>> resultList) {
        CharBuffer charBuffer = CharBuffer.wrap(line);

        int start = 0;
        while (start < charBuffer.length()) { //Allow multiple matches per line
            Match gm = grok.match(charBuffer.subSequence(start, charBuffer.length()));
            if (Boolean.FALSE.equals(gm.isNull())) {
                if (flattened) {
                    resultList.add(gm.captureFlattened());
                } else {
                    resultList.add(gm.capture());
                }
                start += gm.getEnd();
            } else {
                break;
            }

            if (!allowMultipleMatchesPerLine) {
                break;
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        customPatterns.addAll(getCamelContext().getRegistry().findByType(GrokPattern.class));
        refreshGrok();
    }

    @Override
    protected void doStop() throws Exception {
        //noop
    }

}
