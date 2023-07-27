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
package org.apache.camel.component.language;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.TypedLanguageSupport;
import org.apache.camel.util.IOHelper;

/**
 * Execute scripts in any of the languages supported by Camel.
 *
 * By having a component to execute language scripts, it allows more dynamic routing capabilities. For example by using
 * the Routing Slip or Dynamic Router EIPs you can send messages to language endpoints where the script is dynamic
 * defined as well.
 */
@UriEndpoint(firstVersion = "2.5.0", scheme = "language", title = "Language", syntax = "language:languageName:resourceUri",
             producerOnly = true, category = { Category.CORE, Category.SCRIPT }, headersClass = LanguageConstants.class)
public class LanguageEndpoint extends ResourceEndpoint {
    private Language language;
    private Expression expression;
    private boolean contentResolvedFromResource;
    @UriPath(enums = "bean,constant,csimple,datasonnet,exchangeProperty,file,groovy,header,hl7terser,joor,jq,jsonpath"
                     + ",mvel,ognl,ref,simple,spel,sql,tokenize,xpath,xquery,xtokenize")
    @Metadata(required = true)
    private String languageName;
    // resourceUri is optional in the language endpoint
    @UriPath(description = "Path to the resource, or a reference to lookup a bean in the Registry to use as the resource")
    @Metadata(supportFileReference = true)
    private String resourceUri;
    @UriParam
    private String script;
    @UriParam(defaultValue = "true")
    private boolean transform = true;
    @UriParam
    private boolean binary;
    @UriParam
    private boolean cacheScript;
    @UriParam(defaultValue = "true", description = "Sets whether to use resource content cache or not")
    private boolean contentCache;
    @UriParam
    private String resultType;

    public LanguageEndpoint() {
        // enable cache by default
        setContentCache(true);
    }

    public LanguageEndpoint(String endpointUri, Component component, Language language, Expression expression,
                            String resourceUri) {
        super(endpointUri, component, resourceUri);
        this.language = language;
        this.expression = expression;
        // enable cache by default
        setContentCache(true);
    }

    @Override
    protected void doInit() throws Exception {
        if (language == null && languageName != null) {
            language = getCamelContext().resolveLanguage(languageName);
        }
        if (language instanceof TypedLanguageSupport && resultType != null) {
            Class<?> clazz = getCamelContext().getClassResolver().resolveMandatoryClass(resultType);
            ((TypedLanguageSupport) language).setResultType(clazz);
        }
        if (cacheScript && expression == null && script != null) {
            boolean external = script.startsWith("file:") || script.startsWith("http:");
            if (!external) {
                // we can pre optimize this as the script can be loaded from classpath or registry etc
                script = resolveScript(script);
                expression = language.createExpression(script);
            }
        }
        if (expression != null) {
            expression.init(getCamelContext());
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        if (cacheScript && expression == null && script != null) {
            script = resolveScript(script);
            expression = language.createExpression(script);
            expression.init(getCamelContext());
        }

        return new LanguageProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("Cannot consume to a LanguageEndpoint: " + getEndpointUri());
    }

    /**
     * Resolves the script.
     *
     * @param  script      script or uri for a script to load
     * @return             the script
     * @throws IOException is thrown if error loading the script
     */
    protected String resolveScript(String script) throws IOException {
        String answer;

        if (ResourceHelper.hasScheme(script)) {
            InputStream is = loadResource(script);
            answer = getCamelContext().getTypeConverter().convertTo(String.class, is);
            IOHelper.close(is);
        } else if (EndpointHelper.isReferenceParameter(script)) {
            answer = CamelContextHelper.mandatoryLookup(getCamelContext(), script, String.class);
        } else {
            answer = script;
        }

        return answer;
    }

    @Override
    protected String createEndpointUri() {
        String s = script;
        s = URLEncoder.encode(s, StandardCharsets.UTF_8);
        return languageName + ":" + s;
    }

    public Language getLanguage() {
        return language;
    }

    public Expression getExpression() {
        if (isContentResolvedFromResource() && isContentCacheCleared()) {
            return null;
        }
        return expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    public boolean isTransform() {
        return transform;
    }

    /**
     * Whether or not the result of the script should be used as message body.
     * <p/>
     * This options is default <tt>true</tt>.
     *
     * @param transform <tt>true</tt> to use result as new message body, <tt>false</tt> to keep the existing message
     *                  body
     */
    public void setTransform(boolean transform) {
        this.transform = transform;
    }

    public boolean isBinary() {
        return binary;
    }

    /**
     * Whether the script is binary content or text content.
     * <p/>
     * By default the script is read as text content (eg <tt>java.lang.String</tt>)
     *
     * @param binary <tt>true</tt> to read the script as binary, instead of text based.
     */
    public void setBinary(boolean binary) {
        this.binary = binary;
    }

    /**
     * Sets the name of the language to use
     *
     * @param languageName the name of the language
     */
    public void setLanguageName(String languageName) {
        this.languageName = languageName;
    }

    /**
     * Path to the resource, or a reference to lookup a bean in the Registry to use as the resource
     *
     * @param resourceUri the resource path
     */
    @Override
    public void setResourceUri(String resourceUri) {
        super.setResourceUri(resourceUri);
    }

    @Override
    public String getResourceUri() {
        return super.getResourceUri();
    }

    /**
     * Sets the script to execute
     *
     * @param script the script
     */
    public void setScript(String script) {
        this.script = script;
    }

    public String getScript() {
        return script;
    }

    public boolean isContentResolvedFromResource() {
        return contentResolvedFromResource;
    }

    public void setContentResolvedFromResource(boolean contentResolvedFromResource) {
        this.contentResolvedFromResource = contentResolvedFromResource;
    }

    public boolean isCacheScript() {
        return cacheScript;
    }

    /**
     * Whether to cache the compiled script and reuse
     * <p/>
     * Notice reusing the script can cause side effects from processing one Camel {@link org.apache.camel.Exchange} to
     * the next {@link org.apache.camel.Exchange}.
     */
    public void setCacheScript(boolean cacheScript) {
        this.cacheScript = cacheScript;
    }

    public String getResultType() {
        return resultType;
    }

    /**
     * Sets the class of the result type (type from output)
     */
    public void setResultType(String resultType) {
        this.resultType = resultType;
    }

    @Override
    public void clearContentCache() {
        super.clearContentCache();
        // must also clear expression and script
        expression = null;
        script = null;
    }

}
