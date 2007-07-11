/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.model;

import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.Processor;

import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
public abstract class ProcessorType {

    public abstract List<ProcessorType> getOutputs();
    public abstract List<InterceptorRef> getInterceptors();

    public ProcessorType interceptor(String ref) {
        getInterceptors().add(new InterceptorRef(ref));
        return this;
    }

    public ProcessorType interceptors(String... refs) {
        for (String ref : refs) {
            interceptor(ref);
        }
        return this;
    }

    public FilterType filter(ExpressionType expression) {
        FilterType filter = new FilterType();
        filter.setExpression(expression);
        getOutputs().add(filter);
        return filter;
    }

    public FilterType filter(String language, String expression) {
        return filter(new LanguageExpression(language, expression));
    }

    public ProcessorType to(String uri) {
        ToType to = new ToType();
        to.setUri(uri);
        getOutputs().add(to);
        return this;
    }

    public Processor createProcessor(RouteType route) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }
}
