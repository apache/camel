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
package org.apache.camel.language.groovy;

import groovy.lang.Script;
import org.apache.camel.Service;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.util.LRUSoftCache;
import org.codehaus.groovy.runtime.InvokerHelper;

/**
 * @version
 */
public class GroovyLanguage extends LanguageSupport {

    // Cache used to stores the compiled scripts (aka their classes)
    private final LRUSoftCache<String, GroovyClassService> scriptCache = new LRUSoftCache<String, GroovyClassService>(16, 1000, true);

    private static final class GroovyClassService implements Service {

        private final Class<Script> script;

        private GroovyClassService(Class<Script> script) {
            this.script = script;
        }

        @Override
        public void start() throws Exception {
        }

        @Override
        public void stop() throws Exception {
            InvokerHelper.removeClass(script);
        }

    }

    public static GroovyExpression groovy(String expression) {
        return new GroovyLanguage().createExpression(expression);
    }

    public GroovyExpression createPredicate(String expression) {
        return createExpression(expression);
    }

    public GroovyExpression createExpression(String expression) {
        expression = loadResource(expression);
        return new GroovyExpression(expression);
    }

    Class<Script> getScriptFromCache(String script) {
        final GroovyClassService cached = scriptCache.get(script);

        if (cached == null) {
            return null;
        }

        return cached.script;
    }

    void addScriptToCache(String script, Class<Script> scriptClass) {
        scriptCache.put(script, new GroovyClassService(scriptClass));
    }

}
