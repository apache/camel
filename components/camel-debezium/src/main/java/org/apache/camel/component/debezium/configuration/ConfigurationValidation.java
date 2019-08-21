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
package org.apache.camel.component.debezium.configuration;

import org.apache.camel.util.ObjectHelper;

public final class ConfigurationValidation {
    private final boolean isValid;
    private final String reason;

    private ConfigurationValidation(final boolean isValid, final String reason) {
        this.isValid = isValid;
        this.reason = reason;
    }

    public static ConfigurationValidation valid() {
        return new ConfigurationValidation(true, "");
    }

    public static ConfigurationValidation notValid(final String reason) {
        if (ObjectHelper.isEmpty(reason)) {
            throw new IllegalArgumentException("You will need to specify a reason why is not valid");
        }
        return new ConfigurationValidation(false, reason);
    }

    public boolean isValid() {
        return isValid;
    }

    public String getReason() {
        return reason;
    }

}
