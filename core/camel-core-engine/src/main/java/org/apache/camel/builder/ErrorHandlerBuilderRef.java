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

/**
 * Represents a proxy to an error handler builder which is resolved by named
 * reference
 */
public class ErrorHandlerBuilderRef extends ErrorHandlerBuilderSupport {

    public static final String DEFAULT_ERROR_HANDLER_BUILDER = "CamelDefaultErrorHandlerBuilder";

    private final String ref;
    private boolean supportTransacted;

    public ErrorHandlerBuilderRef(String ref) {
        this.ref = ref;
    }

    @Override
    public boolean supportTransacted() {
        return supportTransacted;
    }

    @Override
    public ErrorHandlerBuilder cloneBuilder() {
        ErrorHandlerBuilderRef answer = new ErrorHandlerBuilderRef(ref);
        cloneBuilder(answer);
        return answer;
    }

    protected void cloneBuilder(ErrorHandlerBuilderRef other) {
        super.cloneBuilder(other);

        // no need to copy the handlers

        other.supportTransacted = supportTransacted;
    }

    public String getRef() {
        return ref;
    }

    @Override
    public String toString() {
        return "ErrorHandlerBuilderRef[" + ref + "]";
    }
}
