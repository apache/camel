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
package org.apache.camel.model.errorhandler;

import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.spi.Language;

/**
 * Legacy error handler for XML DSL in camel-spring-xml/camel-blueprint
 */
@XmlTransient
@Deprecated
public class DeadLetterChannelConfiguration extends DefaultErrorHandlerConfiguration implements DeadLetterChannelProperties {

    // has no additional configurations

    @Override
    public Predicate getRetryWhilePolicy(CamelContext context) {
        Predicate answer = getRetryWhile();

        if (getRetryWhileRef() != null) {
            // its a bean expression
            Language bean = context.resolveLanguage("bean");
            answer = bean.createPredicate(getRetryWhileRef());
        }

        return answer;
    }

}
