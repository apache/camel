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
package org.apache.camel.builder;

import org.apache.camel.model.errorhandler.ErrorHandlerRefConfiguration;
import org.apache.camel.model.errorhandler.ErrorHandlerRefProperties;

/**
 * Represents a proxy to an error handler builder which is resolved by named reference
 */
public class ErrorHandlerBuilderRef extends ErrorHandlerBuilderSupport implements ErrorHandlerRefProperties {

    public static final String DEFAULT_ERROR_HANDLER_BUILDER = ErrorHandlerRefProperties.DEFAULT_ERROR_HANDLER_BUILDER;

    private final ErrorHandlerRefConfiguration configuration = new ErrorHandlerRefConfiguration();

    public ErrorHandlerBuilderRef(String ref) {
        this.configuration.setRef(ref);
    }

    @Override
    public boolean supportTransacted() {
        return configuration.isSupportTransacted();
    }

    @Override
    public ErrorHandlerBuilder cloneBuilder() {
        ErrorHandlerBuilderRef answer = new ErrorHandlerBuilderRef(configuration.getRef());
        cloneBuilder(answer);
        return answer;
    }

    protected void cloneBuilder(ErrorHandlerBuilderRef other) {
        other.setSupportTransacted(configuration.isSupportTransacted());
    }

    public String getRef() {
        return configuration.getRef();
    }

    @Override
    public void setRef(String ref) {
        configuration.setRef(ref);
    }

    @Override
    public boolean isSupportTransacted() {
        return configuration.isSupportTransacted();
    }

    @Override
    public void setSupportTransacted(boolean supportTransacted) {
        configuration.setSupportTransacted(supportTransacted);
    }

    @Override
    public String toString() {
        return "ErrorHandlerBuilderRef[" + configuration.getRef() + "]";
    }
}
