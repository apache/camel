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
package org.apache.camel.component.atomix.client.set;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.atomix.client.AtomixClientConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class AtomixSetConfiguration extends AtomixClientConfiguration {
    @UriParam(defaultValue = "ADD")
    private AtomixSet.Action defaultAction = AtomixSet.Action.ADD;
    @UriParam
    private long ttl;

    // ****************************************
    // Properties
    // ****************************************

    public AtomixSet.Action getDefaultAction() {
        return defaultAction;
    }

    /**
     * The default action.
     */
    public void setDefaultAction(AtomixSet.Action defaultAction) {
        this.defaultAction = defaultAction;
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
    public AtomixSetConfiguration copy() {
        try {
            return (AtomixSetConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
