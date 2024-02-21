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
package org.apache.camel.language.xquery;

import net.sf.saxon.Configuration;
import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.component.xquery.XQueryBuilder;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.SingleInputTypedLanguageSupport;
import org.apache.camel.support.component.PropertyConfigurerSupport;

@Language("xquery")
public class XQueryLanguage extends SingleInputTypedLanguageSupport implements PropertyConfigurer {

    private Configuration configuration;

    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * To use an existing Saxon configuration, instead of default settings.
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Expression createExpression(Expression source, String expression, Object[] properties) {
        expression = loadResource(expression);

        XQueryBuilder builder = XQueryBuilder.xquery(expression);
        configureBuilder(builder, properties, source);
        return builder;
    }

    protected void configureBuilder(XQueryBuilder builder, Object[] properties, Expression source) {
        builder.setSource(source);

        Class<?> clazz = property(Class.class, properties, 0, null);
        if (clazz != null) {
            builder.setResultType(clazz);
        }
        if (configuration != null) {
            builder.setConfiguration(configuration);
        }
    }

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }
        switch (ignoreCase ? name.toLowerCase() : name) {
            case "configuration":
            case "Configuration":
                setConfiguration(PropertyConfigurerSupport.property(camelContext, Configuration.class, value));
                return true;
            default:
                return false;
        }
    }

}
