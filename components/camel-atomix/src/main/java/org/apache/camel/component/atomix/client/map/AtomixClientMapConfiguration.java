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
package org.apache.camel.component.atomix.client.map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.atomix.client.AtomixClientAction;
import org.apache.camel.component.atomix.client.AtomixClientConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class AtomixClientMapConfiguration extends AtomixClientConfiguration {
    @UriParam(defaultValue = "PUT")
    private AtomixClientAction defaultAction = AtomixClientAction.PUT;
    @UriParam
    private Long ttl;
    @UriParam
    private String resultHeader;
//    @UriParam(label = "advanced")
//    private DistributedMap.Config config = new DistributedMap.Config();
//    @UriParam(label = "advanced")
//    private DistributedMap.Options options = new DistributedMap.Options();

    // ****************************************
    // Properties
    // ****************************************

    public AtomixClientAction getDefaultAction() {
        return defaultAction;
    }

    /**
     * The default action.
     */
    public void setDefaultAction(AtomixClientAction defaultAction) {
        this.defaultAction = defaultAction;
    }

    public Long getTtl() {
        return ttl;
    }

    /**
     * The resource ttl.
     */
    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }

    public String getResultHeader() {
        return resultHeader;
    }

    /**
     * The header that wil carry the result.
     */
    public void setResultHeader(String resultHeader) {
        this.resultHeader = resultHeader;
    }


//    public DistributedMap.Config getConfig() {
//        return config;
//    }
//
//    /**
//     * The cluster wide map config
//     */
//    public void setConfig(DistributedMap.Config config) {
//        this.config = config;
//    }
//
//    public DistributedMap.Options getOptions() {
//        return options;
//    }
//
//    /**
//     * The local map options
//     */
//    public void setOptions(DistributedMap.Options options) {
//        this.options = options;
//    }

    // ****************************************
    // Copy
    // ****************************************

    public AtomixClientMapConfiguration copy() {
        try {
            return (AtomixClientMapConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
