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
package org.apache.camel.impl.verifier;

import java.util.List;

import org.apache.camel.ComponentVerifier;

public class DefaultResult implements ComponentVerifier.Result {
    private final ComponentVerifier.Scope scope;
    private final Status status;
    private final List<ComponentVerifier.Error> errors;

    public DefaultResult(ComponentVerifier.Scope scope, Status status, List<ComponentVerifier.Error> errors) {
        this.scope = scope;
        this.status = status;
        this.errors = errors;
    }

    @Override
    public ComponentVerifier.Scope getScope() {
        return scope;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public List<ComponentVerifier.Error> getErrors() {
        return errors;
    }

    @Override
    public String toString() {
        return "DefaultResult{" +
            "scope=" + scope +
            ", status=" + status +
            ", errors=" + errors +
            '}';
    }
}
