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
package org.apache.camel.component.language;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.Language;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;

/**
 * Language endpoint.
 *
 * @version 
 */
public class LanguageEndpoint extends ResourceEndpoint {
    private Language language;
    private Expression expression;
    private String languageName;
    private String script;
    private boolean transform = true;
    private boolean contentResolvedFromResource;

    public LanguageEndpoint() {
        // enable cache by default
        setContentCache(true);
    }

    public LanguageEndpoint(String endpointUri, Component component, Language language, Expression expression, String resourceUri) {
        super(endpointUri, component, resourceUri);
        this.language = language;
        this.expression = expression;
        // enable cache by default
        setContentCache(true);
    }

    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(getCamelContext(), "CamelContext", this);

        if (language == null && languageName != null) {
            language = getCamelContext().resolveLanguage(languageName);
        }

        ObjectHelper.notNull(language, "language", this);
        if (expression == null && script != null) {
            script = resolveScript(script);
            expression = language.createExpression(script);
        }

        return new LanguageProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("Cannot consume to a LanguageEndpoint: " + getEndpointUri());
    }

    /**
     * Resolves the script.
     *
     * @param script script or uri for a script to load
     * @return the script
     * @throws IOException is thrown if error loading the script
     */
    protected String resolveScript(String script) throws IOException {
        String answer;
        if (ResourceHelper.hasScheme(script)) {
            InputStream is = loadResource(script);
            answer = getCamelContext().getTypeConverter().convertTo(String.class, is);
            IOHelper.close(is);
        } else {
            answer = script;
        }

        return answer;
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    protected String createEndpointUri() {
        String s = script;
        try {
            s = URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // ignore
        }
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
     * @param transform <tt>true</tt> to use result as new message body, <tt>false</tt> to keep the existing message body
     */
    public void setTransform(boolean transform) {
        this.transform = transform;
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
     * Sets the script to execute
     *
     * @param script the script
     */
    public void setScript(String script) {
        this.script = script;
    }

    public boolean isContentResolvedFromResource() {
        return contentResolvedFromResource;
    }

    public void setContentResolvedFromResource(boolean contentResolvedFromResource) {
        this.contentResolvedFromResource = contentResolvedFromResource;
    }
}
