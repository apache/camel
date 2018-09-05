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
package org.apache.camel.component.chunk;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.Map;

import com.x5.template.Chunk;
import com.x5.template.Theme;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.io.IOUtils;

import static org.apache.camel.component.chunk.ChunkConstants.CHUNK_ENDPOINT_URI_PREFIX;
import static org.apache.camel.component.chunk.ChunkConstants.CHUNK_LAYER_SEPARATOR;
import static org.apache.camel.component.chunk.ChunkConstants.CHUNK_RESOURCE_URI;
import static org.apache.camel.component.chunk.ChunkConstants.CHUNK_TEMPLATE;

/**
 * Transforms the message using a Chunk template.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "chunk", title = "Chunk", syntax = "chunk:resourceUri", producerOnly = true, label = "transformation")
public class ChunkEndpoint extends ResourceEndpoint {

    private Theme theme;
    private Chunk chunk;
    
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
    public boolean isSingleton() {
        return true;
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
        String newResourceUri = exchange.getIn().getHeader(CHUNK_RESOURCE_URI, String.class);
        if (newResourceUri == null) {
            String newTemplate = exchange.getIn().getHeader(CHUNK_TEMPLATE, String.class);
            Chunk newChunk;
            if (newTemplate == null) {
                fromTemplate = false;
                newChunk = getOrCreateChunk(theme, fromTemplate);
            } else {
                fromTemplate = true;
                newChunk = createChunk(new StringReader(newTemplate), theme, fromTemplate);
                exchange.getIn().removeHeader(CHUNK_TEMPLATE);
            }

            // Execute Chunk
            Map<String, Object> variableMap = ExchangeHelper.createVariableMap(exchange);
            StringWriter writer = new StringWriter();
            newChunk.putAll(variableMap);
            newChunk.render(writer);
            writer.flush();

            // Fill out message
            Message out = exchange.getOut();
            out.setBody(newChunk.toString());
            out.setHeaders(exchange.getIn().getHeaders());
            out.setAttachments(exchange.getIn().getAttachments());
        } else {
            exchange.getIn().removeHeader(ChunkConstants.CHUNK_RESOURCE_URI);
            ChunkEndpoint newEndpoint = getCamelContext().getEndpoint(CHUNK_ENDPOINT_URI_PREFIX + newResourceUri, ChunkEndpoint.class);
            newEndpoint.onExchange(exchange);
        }
    }

    /**
     * Create a Chunk template
     *
     * @param resourceReader Reader used to get template
     * @param theme The theme
     * @return Chunk
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
            if (oldcl != null) {
                Thread.currentThread().setContextClassLoader(oldcl);
            }
        }
    }

    private Chunk getOrCreateChunk(Theme theme, boolean fromTemplate) throws IOException {
        if (chunk == null) {
            chunk = createChunk(new StringReader(getResourceUriExtended()), theme, fromTemplate);
        }
        return chunk;
    }
    
    private Theme getOrCreateTheme() throws IOException {
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
    
    private String getResourceUriExtended() throws IOException {
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

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (theme == null) {
            theme = getOrCreateTheme();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        // noop
    }
}
