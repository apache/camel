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
package org.apache.camel.component.thymeleaf;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.DefaultTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.templateresolver.UrlTemplateResolver;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * Transform messages using a Thymeleaf template.
 */
@UriEndpoint(firstVersion = "4.1.0", scheme = "thymeleaf", title = "Thymeleaf", syntax = "thymeleaf:resourceUri",
             remote = false, producerOnly = true, category = { Category.TRANSFORMATION },
             headersClass = ThymeleafConstants.class)
public class ThymeleafEndpoint extends ResourceEndpoint {

    private TemplateEngine templateEngine;
    private String template;
    private JakartaServletWebApplication jakartaServletWebApplication;

    @UriParam(label = "advanced", defaultValue = "CLASS_LOADER",
              description = "The type of resolver to be used by the template engine.",
              javaType = "org.apache.camel.component.thymeleaf.ThymeleafResolverType")
    private ThymeleafResolverType resolver = ThymeleafResolverType.CLASS_LOADER;
    @UriParam(description = "The template mode to be applied to templates.", defaultValue = "HTML",
              enums = "HTML,XML,TEXT,JAVASCRIPT,CSS,RAW")
    private String templateMode;
    @UriParam(label = "advanced",
              description = "An optional prefix added to template names to convert them into resource names.")
    private String prefix;
    @UriParam(label = "advanced",
              description = "An optional suffix added to template names to convert them into resource names.")
    private String suffix;
    @UriParam(label = "advanced", description = "The character encoding to be used for reading template resources.")
    private String encoding;
    @UriParam(label = "advanced",
              description = "The order in which this template will be resolved as part of the resolver chain.")
    private Integer order;
    @UriParam(description = "Whether a template resources will be checked for existence before being returned.")
    private Boolean checkExistence;
    @UriParam(description = "The cache Time To Live for templates, expressed in milliseconds.")
    private Long cacheTimeToLive;
    @UriParam(description = "Whether templates have to be considered cacheable or not.")
    private Boolean cacheable;

    public ThymeleafEndpoint() {
    }

    public ThymeleafEndpoint(String endpointURI, Component component, String resourceURI) {
        super(endpointURI, component, resourceURI);
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return "thymeleaf:" + getResourceUri();
    }

    public String getTemplateMode() {
        return templateMode;
    }

    /**
     * <p>
     * Sets the template mode to be applied to templates resolved by this endpoint.
     * </p>
     * <p>
     * One of {@code HTML}, {@code XML}, {@code TEXT}, {@code JAVASCRIPT}, {@code CSS}, {@code RAW}.
     * </p>
     * <p>
     * Note that this template mode will be ignored if the template resource name ends in a known file name suffix:
     * {@code .html}, {@code .htm}, {@code .xhtml}, {@code .xml}, {@code .js}, {@code .json}, {@code .css},
     * {@code .rss}, {@code .atom}, {@code .txt}.
     * </p>
     *
     * @param templateMode the template mode.
     */
    public void setTemplateMode(String templateMode) {
        this.templateMode = templateMode;
    }

    public ThymeleafResolverType getResolver() {
        return resolver;
    }

    /**
     * Sets the type of resolver to be used by the template engine.
     * <p>
     * One of {@code CLASS_LOADER}, {@code DEFAULT}, {@code FILE}, {@code STRING}, {@code URL}, {@code WEB_APP}.
     * </p>
     */
    public void setResolver(ThymeleafResolverType resolver) {
        this.resolver = resolver;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * <p>
     * Sets a new (optional) prefix to be added to all template names in order to convert <i>template names</i> into
     * <i>resource names</i>.
     * </p>
     *
     * @param prefix the prefix to be set.
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    /**
     * <p>
     * Sets a new (optional) suffix to be added to all template names in order to convert <i>template names</i> into
     * <i>resource names</i>.
     * </p>
     * <p>
     * Note that this suffix may not be applied to the template name if the template name already ends in a known file
     * name suffix: {@code .html}, {@code .htm}, {@code .xhtml}, {@code .xml}, {@code .js}, {@code .json}, {@code .css},
     * {@code .rss}, {@code .atom}, {@code .txt}.
     * </p>
     *
     * @param suffix the suffix to be set.
     */
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets a new character encoding for reading template resources.
     *
     * @param encoding the character encoding to be used.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Integer getOrder() {
        return order;
    }

    /**
     * Sets a new order for the template engine in the chain. Order should start with 1.
     *
     * @param order the new order.
     */
    public void setOrder(Integer order) {
        this.order = order;
    }

    public Boolean getCheckExistence() {
        return checkExistence;
    }

    /**
     * Sets whether template resources will be checked for existence before being returned or not. Default value is
     * {@code FALSE}.
     *
     * @param checkExistence {@code true} if resource existence should be checked, {@code false} if not
     */
    public void setCheckExistence(Boolean checkExistence) {
        this.checkExistence = checkExistence;
    }

    public Long getCacheTimeToLive() {
        return cacheTimeToLive;
    }

    /**
     * Sets a new value for the cache TTL for resolved templates.
     *
     * If a template is resolved as <i>cacheable</i> but cache TTL is null, this means the template will live in cache
     * until evicted by LRU (Least Recently Used) algorithm for being the oldest entry in cache.
     *
     * @param cacheTimeToLive the new cache TTL in milliseconds, or null for using natural LRU eviction.
     */
    public void setCacheTimeToLive(Long cacheTimeToLive) {
        this.cacheTimeToLive = cacheTimeToLive;
    }

    public Boolean getCacheable() {
        return cacheable;
    }

    /**
     * Sets a new value for the <i>cacheable</i> flag.
     *
     * @param cacheable whether resolved patterns should be considered cacheable or not.
     */
    public void setCacheable(Boolean cacheable) {
        this.cacheable = cacheable;
    }

    protected synchronized TemplateEngine getTemplateEngine() {
        if (templateEngine == null) {
            ITemplateResolver templateResolver;

            switch (resolver) {
                case CLASS_LOADER -> {
                    templateResolver = classLoaderTemplateResolver();
                }
                case DEFAULT -> {
                    templateResolver = defaultTemplateResolver();
                }
                case FILE -> {
                    templateResolver = fileTemplateResolver();
                }
                case STRING -> {
                    templateResolver = stringTemplateResolver();
                }
                case URL -> {
                    templateResolver = urlTemplateResolver();
                }
                case WEB_APP -> {
                    templateResolver = webApplicationTemplateResolver();
                }
                default -> {
                    throw new RuntimeCamelException("cannot determine TemplateResolver for type " + resolver);
                }
            }

            templateEngine = new TemplateEngine();
            templateEngine.setTemplateResolver(templateResolver);
        }

        return templateEngine;
    }

    /**
     * To use the {@link TemplateEngine} otherwise a new engine is created
     */
    public void setTemplateEngine(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public void setJakartaServletWebApplication(JakartaServletWebApplication jakartaServletWebApplication) {
        this.jakartaServletWebApplication = jakartaServletWebApplication;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    @Override
    public void clearContentCache() {
        if (templateEngine != null) {
            templateEngine.clearTemplateCache();
        }
    }

    public ThymeleafEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(getResourceUri(), newResourceUri);
        log.debug("Getting endpoint with URI: {}", newUri);
        return getCamelContext().getEndpoint(newUri, ThymeleafEndpoint.class);
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(path, "resourceUri");

        String newResourceUri = exchange.getIn().getHeader(ThymeleafConstants.THYMELEAF_RESOURCE_URI, String.class);
        if (newResourceUri != null) {
            // remove the header so that it is not propagated in the exchange
            exchange.getIn().removeHeader(ThymeleafConstants.THYMELEAF_RESOURCE_URI);

            log.debug("{} set to {}, creating new endpoint to handle exchange",
                    ThymeleafConstants.THYMELEAF_RESOURCE_URI, newResourceUri);
            try (ThymeleafEndpoint newEndpoint = findOrCreateEndpoint(getEndpointUri(), newResourceUri)) {
                newEndpoint.onExchange(exchange);
            }

            return;
        }

        String template = exchange.getIn().getHeader(ThymeleafConstants.THYMELEAF_TEMPLATE, String.class);
        if (template != null) {
            this.template = template;
            // remove the header so that it is not propagated in the exchange
            exchange.getIn().removeHeader(ThymeleafConstants.THYMELEAF_TEMPLATE);
        } else {
            this.template = path;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> dataModel = exchange.getIn().getHeader(ThymeleafConstants.THYMELEAF_VARIABLE_MAP, Map.class);
        if (dataModel == null) {
            dataModel = ExchangeHelper.createVariableMap(exchange, isAllowContextMapAll());
        } else {
            ExchangeHelper.populateVariableMap(exchange, dataModel, isAllowContextMapAll());

            // remove the header so that it is not propagated in the exchange
            exchange.getIn().removeHeader(ThymeleafConstants.THYMELEAF_VARIABLE_MAP);
        }

        // let thymeleaf parse and generate the result
        TemplateEngine templateEngine = getTemplateEngine();
        Context context = new Context();
        context.setVariables(dataModel);
        String buffer = templateEngine.process(this.template, context);

        // store the result in the exchange body
        ExchangeHelper.setInOutBodyPatternAware(exchange, buffer);
    }

    private ITemplateResolver classLoaderTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        if (cacheable != null) {
            resolver.setCacheable(cacheable);
        }
        if (cacheTimeToLive != null) {
            resolver.setCacheTTLMs(cacheTimeToLive);
        }
        if (encoding != null) {
            resolver.setCharacterEncoding(encoding);
        }
        if (checkExistence != null) {
            resolver.setCheckExistence(checkExistence);
        }
        if (order != null) {
            resolver.setOrder(order);
        }
        if (prefix != null) {
            resolver.setPrefix(prefix);
        }
        if (suffix != null) {
            resolver.setSuffix(suffix);
        }
        if (templateMode != null) {
            resolver.setTemplateMode(templateMode);
        }
        if (template == null) {
            throw new RuntimeCamelException("template must be provided");
        }

        return resolver;
    }

    private ITemplateResolver defaultTemplateResolver() {
        DefaultTemplateResolver resolver = new DefaultTemplateResolver();
        if (checkExistence != null) {
            resolver.setCheckExistence(checkExistence);
        }
        if (order != null) {
            resolver.setOrder(order);
        }
        if (templateMode != null) {
            resolver.setTemplateMode(templateMode);
        }
        if (template == null) {
            throw new RuntimeCamelException("template must be provided");
        } else {
            resolver.setTemplate(template);
        }

        return resolver;
    }

    private ITemplateResolver fileTemplateResolver() {
        FileTemplateResolver resolver = new FileTemplateResolver();
        if (cacheable != null) {
            resolver.setCacheable(cacheable);
        }
        if (cacheTimeToLive != null) {
            resolver.setCacheTTLMs(cacheTimeToLive);
        }
        if (encoding != null) {
            resolver.setCharacterEncoding(encoding);
        }
        if (checkExistence != null) {
            resolver.setCheckExistence(checkExistence);
        }
        if (order != null) {
            resolver.setOrder(order);
        }
        if (prefix != null) {
            resolver.setPrefix(prefix);
        }
        if (suffix != null) {
            resolver.setSuffix(suffix);
        }
        if (templateMode != null) {
            resolver.setTemplateMode(templateMode);
        }
        if (template == null) {
            throw new RuntimeCamelException("template must be provided");
        }

        return resolver;
    }

    private ITemplateResolver stringTemplateResolver() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        if (cacheable != null) {
            resolver.setCacheable(cacheable);
        }
        if (cacheTimeToLive != null) {
            resolver.setCacheTTLMs(cacheTimeToLive);
        }
        if (checkExistence != null) {
            resolver.setCheckExistence(checkExistence);
        }
        if (order != null) {
            resolver.setOrder(order);
        }
        if (templateMode != null) {
            resolver.setTemplateMode(templateMode);
        }
        if (template == null) {
            throw new RuntimeCamelException("template must be provided");
        }

        return resolver;
    }

    private ITemplateResolver urlTemplateResolver() {
        UrlTemplateResolver resolver = new UrlTemplateResolver();
        if (cacheable != null) {
            resolver.setCacheable(cacheable);
        }
        if (cacheTimeToLive != null) {
            resolver.setCacheTTLMs(cacheTimeToLive);
        }
        if (encoding != null) {
            resolver.setCharacterEncoding(encoding);
        }
        if (order != null) {
            resolver.setOrder(order);
        }
        if (prefix != null) {
            resolver.setPrefix(prefix);
        }
        if (suffix != null) {
            resolver.setSuffix(suffix);
        }
        if (templateMode != null) {
            resolver.setTemplateMode(templateMode);
        }
        if (template == null) {
            throw new RuntimeCamelException("template must be provided");
        }

        return resolver;
    }

    private ITemplateResolver webApplicationTemplateResolver() {
        WebApplicationTemplateResolver resolver = new WebApplicationTemplateResolver(jakartaServletWebApplication);

        if (cacheable != null) {
            resolver.setCacheable(cacheable);
        }
        if (cacheTimeToLive != null) {
            resolver.setCacheTTLMs(cacheTimeToLive);
        }
        if (encoding != null) {
            resolver.setCharacterEncoding(encoding);
        }
        if (checkExistence != null) {
            resolver.setCheckExistence(checkExistence);
        }
        if (order != null) {
            resolver.setOrder(order);
        }
        if (prefix != null) {
            resolver.setPrefix(prefix);
        }
        if (suffix != null) {
            resolver.setSuffix(suffix);
        }
        if (templateMode != null) {
            resolver.setTemplateMode(templateMode);
        }
        if (template == null) {
            throw new RuntimeCamelException("template must be provided");
        }

        return resolver;
    }

}
