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

import org.apache.camel.ErrorHandlerFactory;

/**
 * Legacy error handler for XML DSL in camel-spring-xml/camel-blueprint
 */
@Deprecated(since = "3.17.0")
@XmlTransient
public class ErrorHandlerRefConfiguration implements ErrorHandlerRefProperties {

    private String ref;
    private boolean supportTransacted;

    @Override
    public String getRef() {
        return ref;
    }

    @Override
    public void setRef(String ref) {
        this.ref = ref;
    }

    @Override
    public boolean isSupportTransacted() {
        return supportTransacted;
    }

    @Override
    public void setSupportTransacted(boolean supportTransacted) {
        this.supportTransacted = supportTransacted;
    }

    @Override
    public boolean supportTransacted() {
        return isSupportTransacted();
    }

    @Override
    public ErrorHandlerFactory cloneBuilder() {
        ErrorHandlerRefConfiguration answer = new ErrorHandlerRefConfiguration();
        answer.setRef(ref);
        answer.setSupportTransacted(supportTransacted);
        return answer;
    }
}
