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
package org.apache.camel.tooling.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

public final class ApiModel {

    private String name;
    private String description;
    private boolean consumerOnly;
    private boolean producerOnly;
    private final List<String> aliases = new ArrayList<>();
    // lets sort api methods A..Z so they are always in the same order
    private final Collection<ApiMethodModel> methods = new TreeSet<>(Comparators.apiMethodModelModelComparator());

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isConsumerOnly() {
        return consumerOnly;
    }

    public void setConsumerOnly(boolean consumerOnly) {
        this.consumerOnly = consumerOnly;
    }

    public boolean isProducerOnly() {
        return producerOnly;
    }

    public void setProducerOnly(boolean producerOnly) {
        this.producerOnly = producerOnly;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void addAlias(String alias) {
        this.aliases.add(alias);
    }

    public Collection<ApiMethodModel> getMethods() {
        return methods;
    }

    public ApiMethodModel newMethod(String methodName) {
        ApiMethodModel method = new ApiMethodModel();
        method.setName(methodName);
        this.methods.add(method);
        return method;
    }
}
