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
package org.apache.camel.language.spel;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Service;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.util.RegistryBeanResolver;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.util.ObjectHelper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.BeanResolver;

/**
 * A Spring Expression {@link org.apache.camel.spi.Language} plugin
 */
@Language("spel")
public class SpelLanguage extends LanguageSupport implements Service {

    private BeanResolver beanResolver;

    @Override
    public Predicate createPredicate(String expression) {
        expression = loadResource(expression);
        return new SpelExpression(expression, Boolean.class, beanResolver);
    }

    @Override
    public Expression createExpression(String expression) {
        expression = loadResource(expression);
        return new SpelExpression(expression, Object.class, beanResolver);
    }

    @Override
    public void start() {
        ObjectHelper.notNull(getCamelContext(), "CamelContext", this);

        if (getCamelContext() instanceof SpringCamelContext) {
            ApplicationContext applicationContext = ((SpringCamelContext) getCamelContext()).getApplicationContext();
            beanResolver = new BeanFactoryResolver(applicationContext);
        } else {
            beanResolver = new RegistryBeanResolver(getCamelContext().getRegistry());
        }
    }

    @Override
    public void stop() {
        // noop
    }
}
