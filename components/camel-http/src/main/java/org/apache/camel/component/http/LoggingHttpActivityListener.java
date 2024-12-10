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
package org.apache.camel.component.http;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.MaskingFormatter;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.processor.DefaultMaskingFormatter;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;

import static org.apache.camel.support.LoggerHelper.getLineNumberLoggerName;

@Metadata(label = "bean",
          description = "Logs HTTP requests and responses for the camel-http component.",
          annotations = { "interfaceName=org.apache.camel.component.http.HttpActivityListener" })
@Configurer(metadataOnly = true)
public class LoggingHttpActivityListener extends ServiceSupport implements CamelContextAware, HttpActivityListener {

    private CamelContext camelContext;
    private MaskingFormatter maskingFormatter;

    @Metadata(defaultValue = "INFO", enums = "TRACE,DEBUG,INFO,WARN,ERROR,OFF")
    private String loggingLevel;
    @Metadata(defaultValue = "true", description = "Show route ID.")
    private boolean showRouteId = true;
    @Metadata(defaultValue = "true", description = "Show route Group.")
    private boolean showRouteGroup = true;
    @Metadata(defaultValue = "true", description = "Show the unique exchange ID.")
    private boolean showExchangeId = true;
    @Metadata(defaultValue = "true", description = "Show the HTTP body.")
    private boolean showBody = true;
    @Metadata(defaultValue = "true",
              description = "Whether to show HTTP body that are streaming based. Beware that Camel will have to read the content into memory to print to log, and will re-create the HttpEntity stored on the request/response object. If you have large payloads then this can impact performance.")
    private boolean showStreams = true;
    @Metadata(defaultValue = "true", label = "formatting", description = "Show the HTTP headers.")
    private boolean showHeaders = true;
    @Metadata(defaultValue = "50000", description = "Limits the number of characters logged from the HTTP body.")
    private int maxChars = 50000;
    @Metadata(description = "If true, mask sensitive information like password or passphrase in the log.")
    private Boolean logMask;
    @Metadata(defaultValue = "true", description = "If enabled then each information is outputted as separate LOG events.")
    private boolean multiline;
    @Metadata(defaultValue = "true",
              description = "If enabled then the source location of where the log endpoint is used in Camel routes, would be used as logger name, instead"
                            + " of the given name. However, if the source location is disabled or not possible to resolve then the existing logger name will be used.")
    private boolean sourceLocationLoggerName = true;

    @Override
    protected void doInit() throws Exception {
        maskingFormatter = getCamelContext().getRegistry()
                .lookupByNameAndType(MaskingFormatter.CUSTOM_LOG_MASK_REF, MaskingFormatter.class);
        if (maskingFormatter == null) {
            maskingFormatter = new DefaultMaskingFormatter();
        }
    }

    @Override
    public void onRequestSubmitted(Object source, Exchange exchange, HttpHost httpHost, ClassicHttpRequest request) {
        CamelLogger logger = getLogger(source, exchange);
        if (logger.shouldLog()) {
            onActivity(logger, exchange, httpHost, request, null, -1);
        }
    }

    @Override
    public void onResponseReceived(
            Object source, Exchange exchange, HttpHost httpHost, ClassicHttpResponse response, long elapsed) {
        CamelLogger logger = getLogger(source, exchange);
        if (logger.shouldLog()) {
            onActivity(logger, exchange, httpHost, null, response, elapsed);
        }
    }

    protected void onActivity(
            CamelLogger logger, Exchange exchange, HttpHost httpHost, ClassicHttpRequest request, ClassicHttpResponse response,
            long elapsed) {
        final StringJoiner top = new StringJoiner("");
        final List<String> lines = new ArrayList<>();

        String routeId = ExchangeHelper.getRouteId(exchange);
        String routeGroup = ExchangeHelper.getRouteGroup(exchange);
        String exchangeId = exchange.getExchangeId();
        String host = httpHost.toHostString();
        String protocol = null;
        if (request != null) {
            protocol = request.getVersion() != null ? request.getVersion().toString() : "HTTP/1.1";
        }

        if (request != null) {
            top.add("Sending HTTP Request   (");
        } else {
            top.add("Received HTTP Response (");
        }
        top.add(String.format("host: %s", host));
        if (showRouteGroup && showRouteId) {
            if (routeGroup != null && routeId != null) {
                top.add(String.format(" route: %s/%s", routeGroup, routeId));
            } else if (routeId != null) {
                top.add(String.format(" route: %s", routeId));
            }
        }
        if (showExchangeId) {
            top.add(String.format(" exchangeId: %s", exchangeId));
        }
        if (elapsed != -1) {
            top.add(String.format(" elapsed: %sms", elapsed));
        }
        top.add(")");
        if (request != null) {
            lines.add(String.format("%s %s %s", request.getMethod(), request.getPath(), protocol));
        } else {
            lines.add(String.format("%s %s %s", response.getVersion().toString(), response.getCode(),
                    response.getReasonPhrase()));
        }
        if (showHeaders) {
            Header[] headers = request != null ? request.getHeaders() : response.getHeaders();
            for (Header h : headers) {
                lines.add(String.format("%s: %s", h.getName(), getValue(h.isSensitive(), h.getValue())));
            }
        }
        if (showBody) {
            lines.add(""); // empty line before body
            try {
                var e = request != null ? request.getEntity() : response.getEntity();
                if (e != null) {
                    if (e.isStreaming() && !showStreams) {
                        lines.add("WARN: Cannot log HTTP body because the body is streaming");
                    } else {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        e.writeTo(bos);
                        String data = bos.toString();
                        if (data.length() > maxChars) {
                            data = data.substring(0, maxChars) + " ... [Body clipped after " + maxChars
                                   + " chars, total length is " + data.length() + "]";
                        }
                        lines.add(getValue(false, data));
                        if (!e.isRepeatable()) {
                            // need to re-create body as stream is EOL
                            byte[] arr = bos.toByteArray();
                            ContentType ct = null;
                            if (e.getContentType() != null) {
                                ct = ContentType.parse(e.getContentType());
                            }
                            e = new ByteArrayEntity(arr, ct);
                            if (request != null) {
                                request.setEntity(e);
                            } else {
                                response.setEntity(e);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }

        if (multiline) {
            logger.log(top.toString());
            lines.forEach(logger::log);
        } else {
            StringJoiner sj = new StringJoiner(System.lineSeparator());
            sj.add(top.toString());
            lines.forEach(sj::add);
            logger.log(sj.toString());
        }
    }

    private CamelLogger getLogger(Object source, Exchange exchange) {
        String name = null;
        if (sourceLocationLoggerName) {
            name = getLineNumberLoggerName(source);
        }
        if (name == null) {
            name = LoggingHttpActivityListener.class.getName();
        }
        LoggingLevel level = LoggingLevel.INFO;
        if (loggingLevel != null && !loggingLevel.equals("INFO")) {
            level = LoggingLevel.valueOf(loggingLevel);
        }
        return new CamelLogger(name, level);
    }

    private String getValue(boolean sensitive, Object value) {
        String v = value != null ? value.toString() : null;
        if (v != null && (sensitive || logMask != null && logMask)) {
            v = maskingFormatter.format(v);
        }
        return v;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getLoggingLevel() {
        return loggingLevel;
    }

    public void setLoggingLevel(String loggingLevel) {
        this.loggingLevel = loggingLevel;
    }

    public boolean isShowRouteId() {
        return showRouteId;
    }

    public void setShowRouteId(boolean showRouteId) {
        this.showRouteId = showRouteId;
    }

    public boolean isShowRouteGroup() {
        return showRouteGroup;
    }

    public void setShowRouteGroup(boolean showRouteGroup) {
        this.showRouteGroup = showRouteGroup;
    }

    public boolean isShowExchangeId() {
        return showExchangeId;
    }

    public void setShowExchangeId(boolean showExchangeId) {
        this.showExchangeId = showExchangeId;
    }

    public boolean isShowBody() {
        return showBody;
    }

    public void setShowBody(boolean showBody) {
        this.showBody = showBody;
    }

    public boolean isShowStreams() {
        return showStreams;
    }

    public void setShowStreams(boolean showStreams) {
        this.showStreams = showStreams;
    }

    public boolean isShowHeaders() {
        return showHeaders;
    }

    public void setShowHeaders(boolean showHeaders) {
        this.showHeaders = showHeaders;
    }

    public Boolean getLogMask() {
        return logMask;
    }

    public void setLogMask(Boolean logMask) {
        this.logMask = logMask;
    }

    public int getMaxChars() {
        return maxChars;
    }

    public void setMaxChars(int maxChars) {
        this.maxChars = maxChars;
    }

    public boolean isMultiline() {
        return multiline;
    }

    public void setMultiline(boolean multiline) {
        this.multiline = multiline;
    }

    public boolean isSourceLocationLoggerName() {
        return sourceLocationLoggerName;
    }

    public void setSourceLocationLoggerName(boolean sourceLocationLoggerName) {
        this.sourceLocationLoggerName = sourceLocationLoggerName;
    }
}
