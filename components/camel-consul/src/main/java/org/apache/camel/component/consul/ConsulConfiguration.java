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
package org.apache.camel.component.consul;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class ConsulConfiguration extends ConsulClientConfiguration {
    @UriParam
    private String key;
    @UriParam(label = "producer")
    private String action;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean valueAsString;

    public ConsulConfiguration() {
    }

    public String getAction() {
        return action;
    }

    /**
     * The default action. Can be overridden by CamelConsulAction
     */
    public void setAction(String action) {
        this.action = action;
    }

    public boolean isValueAsString() {
        return valueAsString;
    }

    /**
     * Default to transform values retrieved from Consul i.e. on KV endpoint to
     * string.
     */
    public void setValueAsString(boolean valueAsString) {
        this.valueAsString = valueAsString;
    }

    public String getKey() {
        return key;
    }

    /**
     * The default key. Can be overridden by CamelConsulKey
     */
    public void setKey(String key) {
        this.key = key;
    }

    // ****************************************
    // Copy
    // ****************************************

    @Override
    public ConsulConfiguration copy() {
        try {
            return (ConsulConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
