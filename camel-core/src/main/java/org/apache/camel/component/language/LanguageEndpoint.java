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

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ObjectHelper;

/**
 * Language endpoint.
 *
 * @version $Revision$
 */
public class LanguageEndpoint extends DefaultEndpoint {
    private Language language;
    private Expression expression;
    private String languageName;
    private String script;
    private boolean transform = true;

    public LanguageEndpoint() {
    }

    public LanguageEndpoint(String endpointUri, Component component, Language language, Expression expression) {
        super(endpointUri, component);
        this.language = language;
        this.expression = expression;
    }

    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(getCamelContext(), "CamelContext", this);

        if (language == null && languageName != null) {
            language = getCamelContext().resolveLanguage(languageName);
        }

        ObjectHelper.notNull(language, "language", this);
        if (expression == null && script != null) {
            expression = language.createExpression(script);
        }

        return new LanguageProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("Cannot consume to a LanguageEndpoint: " + getEndpointUri());
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    protected String createEndpointUri() {
        return languageName + ":" + script;
    }

    public Language getLanguage() {
        return language;
    }

    public Expression getExpression() {
        return expression;
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

}
