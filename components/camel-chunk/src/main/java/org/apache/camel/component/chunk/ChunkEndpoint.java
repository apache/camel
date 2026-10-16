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
package org.apache.camel.component.chunk;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.x5.template.Chunk;
import com.x5.template.Snippet;
import com.x5.template.Theme;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.commons.io.IOUtils;

import static org.apache.camel.component.chunk.ChunkConstants.CHUNK_ENDPOINT_URI_PREFIX;
import static org.apache.camel.component.chunk.ChunkConstants.CHUNK_LAYER_SEPARATOR;
import static org.apache.camel.component.chunk.ChunkConstants.CHUNK_RESOURCE_URI;
import static org.apache.camel.component.chunk.ChunkConstants.CHUNK_TEMPLATE;

/**
 * Transform messages using Chunk templating engine.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "chunk", title = "Chunk", syntax = "chunk:resourceUri", producerOnly = true,
             remote = false, category = { Category.TRANSFORMATION }, headersClass = ChunkConstants.class)
public class ChunkEndpoint extends ResourceEndpoint {

    private Theme theme;
    private Chunk chunk;

    @UriParam(defaultValue = "false")
    private boolean allowTemplateFromHeader;
    @UriParam(description = "Define the encoding of the body")
    private String encoding;
    @UriParam(description = "Define the themes folder to scan")
    private String themeFolder;
    @UriParam(description = "Define the themes subfolder to scan")
    private String themeSubfolder;
    @UriParam(description = "Define the theme layer to elaborate")
    private String themeLayer;
    @UriParam(description = "Define the file extension of the template")
    private String extension;

    public ChunkEndpoint() {
    }

    public ChunkEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component, resourceUri);
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
        return CHUNK_ENDPOINT_URI_PREFIX + getResourceUri();
    }

    @Override
    public void clearContentCache() {
        this.chunk = null;
        super.clearContentCache();
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        boolean fromTemplate;

        String newResourceUri = null;
        if (allowTemplateFromHeader) {
            newResourceUri = exchange.getIn().getHeader(CHUNK_RESOURCE_URI, String.class);
        }
        if (newResourceUri == null) {
            String newTemplate = null;
            if (allowTemplateFromHeader) {
                newTemplate = exchange.getIn().getHeader(CHUNK_TEMPLATE, String.class);
            }
            Chunk newChunk;
            if (newTemplate == null) {
                fromTemplate = false;
                newChunk = getOrCreateChunk(theme, fromTemplate);
            } else {
                fromTemplate = true;
                newChunk = createChunk(new StringReader(newTemplate), theme, fromTemplate);
                exchange.getIn().removeHeader(CHUNK_TEMPLATE);
            }

            // Execute Chunk. Bind the message data as literal values so that the Chunk engine renders
            // the body, headers and variables as-is and does not re-parse (and expand) any template tags
            // that may be present inside the data itself. Only the route author's template is parsed as
            // template code.
            Map<String, Object> variableMap = ExchangeHelper.createVariableMap(exchange, isAllowContextMapAll());
            HeadersMapFactory factory = getCamelContext().getCamelContextExtension().getHeadersMapFactory();
            variableMap.replaceAll((key, value) -> toLiteralValue(value, factory));
            StringWriter writer = new StringWriter();
            newChunk.putAll(variableMap);
            newChunk.render(writer);
            writer.flush();

            // Fill out message
            ExchangeHelper.setInOutBodyPatternAware(exchange, writer.toString());
        } else {
            exchange.getIn().removeHeader(ChunkConstants.CHUNK_RESOURCE_URI);
            ChunkEndpoint newEndpoint
                    = getCamelContext().getEndpoint(CHUNK_ENDPOINT_URI_PREFIX + newResourceUri, ChunkEndpoint.class);
            newEndpoint.onExchange(exchange);
        }
    }

    /**
     * Neutralizes message data so the Chunk engine does not parse and expand template tags present inside the data
     * itself when it is printed with a direct {@code {$tag}}. A String (or other {@link CharSequence}) value is wrapped
     * as a literal Chunk snippet only when it actually contains Chunk tags (as reported by Chunk's own parser via
     * {@link Snippet#isSimple()}); tag-free values are left as-is so Chunk's string filters (for example {@code slice},
     * {@code get}, {@code join}) keep their existing behaviour. Nested {@link Map}, {@link Collection} and array values
     * (message headers, variables, multi-value headers, unmarshalled JSON, ...) are copied and processed recursively,
     * preserving a case-insensitive headers map. Other value types are left unchanged.
     * <p/>
     * This does not cover every case: Chunk re-parses any string it produces while rendering, so the output of a filter
     * ({@code {$body|trim}}), a string property of a bean body ({@code {$body.name}}, {@code {$exception.message}}) and
     * string items produced inside a {@code {% loop %}} are still parsed as template code. Untrusted data must not be
     * passed through Chunk filters, bean properties or loops.
     */
    @SuppressWarnings("unchecked")
    private static Object toLiteralValue(Object value, HeadersMapFactory factory) {
        if (value instanceof String) {
            return toLiteralString((String) value);
        }
        if (value instanceof CharSequence) {
            // StringBuilder/StringBuffer and other CharSequences are otherwise stringified and re-parsed by Chunk
            return toLiteralString(value.toString());
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Object> copy = factory.isInstanceOf((Map<String, Object>) value)
                    ? factory.newMap() : new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), toLiteralValue(entry.getValue(), factory));
            }
            return copy;
        }
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object item : collection) {
                copy.add(toLiteralValue(item, factory));
            }
            return copy;
        }
        if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            List<Object> copy = new ArrayList<>(array.length);
            for (Object item : array) {
                copy.add(toLiteralValue(item, factory));
            }
            return copy;
        }
        return value;
    }

    /**
     * Wraps the given string as a literal Chunk snippet when it contains Chunk tags, otherwise returns it unchanged so
     * tag-free data keeps its normal (filter-aware) behaviour.
     */
    private static Object toLiteralString(String str) {
        if (!Snippet.getSnippet(str).isSimple()) {
            return Snippet.makeLiteralSnippet(str);
        }
        return str;
    }

    /**
     * Create a Chunk template
     *
     * @param  resourceReader Reader used to get template
     * @param  theme          The theme
     * @return                Chunk
     */
    private Chunk createChunk(Reader resourceReader, Theme theme, boolean fromTemplate) throws IOException {
        ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader apcl = getCamelContext().getApplicationContextClassLoader();
            if (apcl != null) {
                Thread.currentThread().setContextClassLoader(apcl);
            }
            Chunk newChunk;
            if (fromTemplate) {
                newChunk = theme.makeChunk();
                String targetString = IOUtils.toString(resourceReader);
                newChunk.append(targetString);
            } else {
                String targetString = IOUtils.toString(resourceReader);
                newChunk = theme.makeChunk(targetString);
            }
            return newChunk;
        } finally {
            resourceReader.close();
            Thread.currentThread().setContextClassLoader(oldcl);
        }
    }

    private Chunk getOrCreateChunk(Theme theme, boolean fromTemplate) throws IOException {
        if (chunk == null) {
            if (ResourceHelper.hasScheme(getResourceUri())) {
                InputStream is = getResourceAsInputStream();
                String text = IOHelper.loadText(is);
                chunk = createChunk(new StringReader(text), theme, true);
            } else {
                chunk = createChunk(new StringReader(getResourceUriExtended()), theme, fromTemplate);
            }
        }
        return chunk;
    }

    private Theme getOrCreateTheme() {
        if (theme == null) {
            if (themeFolder == null && themeSubfolder == null) {
                theme = new Theme();
            } else if (themeFolder != null && themeSubfolder == null) {
                URL url = getCamelContext().getClassResolver().loadResourceAsURL(themeFolder);
                theme = new Theme(url.getPath(), "");
            } else {
                URL url = getCamelContext().getClassResolver().loadResourceAsURL(themeFolder);
                theme = new Theme(url.getPath(), themeSubfolder);
            }
            if (encoding != null) {
                theme.setEncoding(encoding);
            }

            ClassLoader apcl = getCamelContext().getApplicationContextClassLoader();
            if (apcl != null) {
                theme.setJarContext(apcl);
            }
        }
        return theme;
    }

    @Override
    public String getResourceUri() {
        String uri = super.getResourceUri();
        if (uri != null && (uri.startsWith("/") || uri.startsWith("\\"))) {
            return uri.substring(1);
        } else {
            return uri;
        }
    }

    private String getResourceUriExtended() {
        return themeLayer == null
                ? getResourceUri()
                : getResourceUri() + CHUNK_LAYER_SEPARATOR + themeLayer;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getThemeFolder() {
        return themeFolder;
    }

    public void setThemeFolder(String themeFolder) {
        this.themeFolder = themeFolder;
    }

    public String getThemeSubfolder() {
        return themeSubfolder;
    }

    public void setThemeSubfolder(String themeSubfolder) {
        this.themeSubfolder = themeSubfolder;
    }

    public String getThemeLayer() {
        return themeLayer;
    }

    public void setThemeLayer(String themeLayer) {
        this.themeLayer = themeLayer;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
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

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (theme == null) {
            theme = getOrCreateTheme();
        }
    }
}
