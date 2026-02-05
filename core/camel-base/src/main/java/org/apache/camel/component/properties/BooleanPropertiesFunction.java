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
package org.apache.camel.component.properties;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;

/**
 * A {@link PropertiesFunction} that evaluates whether a property placeholder matches a given condition
 */
public class BooleanPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    private CamelContext camelContext;
    private Language lan;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getName() {
        return "boolean";
    }

    @Override
    public String apply(String remainder) {
        String key = StringHelper.before(remainder, " ", remainder);
        String bool = StringHelper.after(remainder, " ");
        if (bool != null) {
            bool = bool.trim();
        }

        // key is property placeholder key
        String simple = "${properties:" + key + "}";
        if (bool != null) {
            simple += " " + bool;
        }

        Predicate pred = lan.createPredicate(simple);
        Exchange dummy = new DefaultExchange(camelContext);
        boolean matches = pred.matches(dummy);

        return matches ? "true" : "false";
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        lan = camelContext.resolveLanguage("simple");
    }
}
