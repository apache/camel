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
package org.apache.camel.model;

/**
 * Various constants.
 *
 * @version 
 */
public final class Constants {

    public static final String JAXB_CONTEXT_PACKAGES = ""
        + "org.apache.camel:"
        + "org.apache.camel.model:"
        + "org.apache.camel.model.cloud:"
        + "org.apache.camel.model.config:"
        + "org.apache.camel.model.dataformat:"
        + "org.apache.camel.model.language:"
        + "org.apache.camel.model.loadbalancer:"
        + "org.apache.camel.model.rest:"
        + "org.apache.camel.model.transformer:"
        + "org.apache.camel.model.validator";

    public static final String PLACEHOLDER_QNAME = "http://camel.apache.org/schema/placeholder";

    public static final String CUSTOM_LOG_MASK_REF = "CamelCustomLogMask";

    private Constants() {
    }

}
