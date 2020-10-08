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
package org.apache.camel.main;

import java.util.Objects;

import org.apache.camel.util.ObjectHelper;

final class PropertyOptionKey {
    private final Object instance;
    private final String optionPrefix;

    PropertyOptionKey(Object instance, String optionPrefix) {
        this.instance = ObjectHelper.notNull(instance, "instance");
        this.optionPrefix = ObjectHelper.notNull(optionPrefix, "optionPrefix");
    }

    public Object getInstance() {
        return instance;
    }

    public String getOptionPrefix() {
        return optionPrefix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PropertyOptionKey)) {
            return false;
        }
        PropertyOptionKey key = (PropertyOptionKey) o;
        return Objects.equals(instance, key.instance)
                && Objects.equals(optionPrefix, key.optionPrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instance, optionPrefix);
    }
}
