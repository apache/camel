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
package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model;

import java.util.Map;

public final class AddonsBuilder {
    private Map<String, Object> additionalProperties;

    public AddonsBuilder() {
    }

    public AddonsBuilder(Addons other) {
        this.additionalProperties = other.getAdditionalProperties();
    }

    public static AddonsBuilder addons() {
        return new AddonsBuilder();
    }

    public AddonsBuilder withAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    public Addons build() {
        Addons addons = new Addons();
        addons.setAdditionalProperties(additionalProperties);
        return addons;
    }
}
