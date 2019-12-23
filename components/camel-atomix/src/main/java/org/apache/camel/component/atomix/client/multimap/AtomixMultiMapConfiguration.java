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
package org.apache.camel.component.atomix.client.multimap;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.atomix.client.AtomixClientConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class AtomixMultiMapConfiguration extends AtomixClientConfiguration {
    @UriParam(defaultValue = "PUT")
    private AtomixMultiMap.Action defaultAction = AtomixMultiMap.Action.PUT;
    @UriParam
    private Object key;
    @UriParam
    private long ttl;

    // ****************************************
    // Properties
    // ****************************************

    public AtomixMultiMap.Action getDefaultAction() {
        return defaultAction;
    }

    /**
     * The default action.
     */
    public void setDefaultAction(AtomixMultiMap.Action defaultAction) {
        this.defaultAction = defaultAction;
    }

    public Object getKey() {
        return key;
    }

    /**
     * The key to use if none is set in the header or to listen for events for
     * a specific key.
     */
    public void setKey(Object defaultKey) {
        this.key = defaultKey;
    }

    public long getTtl() {
        return ttl;
    }

    /**
     * The resource ttl.
     */
    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    // ****************************************
    // Copy
    // ****************************************

    @Override
    public AtomixMultiMapConfiguration copy() {
        try {
            return (AtomixMultiMapConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
