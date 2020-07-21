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
package org.apache.camel.spring.config;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderSupport;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.support.processor.DelegateProcessor;
import org.springframework.beans.factory.BeanNameAware;

public class DummyErrorHandlerBuilder extends ErrorHandlerBuilderSupport implements BeanNameAware {

    public static final String PROPERTY_NAME = "DummyErrorHandler";

    static {
        ErrorHandlerReifier.registerReifier(DummyErrorHandlerBuilder.class, DummyErrorHandlerReifier::new);
    }

    private String beanName;

    public DummyErrorHandlerBuilder() {
    }

    @Override
    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public String getBeanName() {
        return beanName;
    }

    @Override
    public ErrorHandlerBuilder cloneBuilder() {
        DummyErrorHandlerBuilder answer = new DummyErrorHandlerBuilder();
        super.cloneBuilder(answer);
        answer.beanName = beanName;
        return answer;
    }

    public static class DummyErrorHandlerReifier extends ErrorHandlerReifier<DummyErrorHandlerBuilder> {

        public DummyErrorHandlerReifier(Route route, ErrorHandlerFactory definition) {
            super(route, (DummyErrorHandlerBuilder) definition);
        }

        @Override
        public Processor createErrorHandler(Processor processor) throws Exception {
            return new DelegateProcessor(processor) {
                @Override
                public void process(Exchange exchange) throws Exception {
                    exchange.setProperty(PROPERTY_NAME, definition.getBeanName());
                    super.process(exchange);
                }
            };
        }
    }

}
