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
package org.apache.camel.component.etcd3;

import org.apache.camel.spi.Metadata;

public abstract class Etcd3Constants {

    @Metadata(label = "producer", description = "The action to perform.\n" +
                                                "Supported values:\n" +
                                                "\n" +
                                                "* set\n" +
                                                "* get\n" +
                                                "* delete\n",
              javaType = "String")
    public static final String ETCD_ACTION = "CamelEtcdAction";
    @Metadata(description = "The target path", javaType = "String")
    public static final String ETCD_PATH = "CamelEtcdPath";
    @Metadata(label = "producer",
              description = "To apply an action on all the key-value pairs whose key that starts with the target path.",
              javaType = "Boolean")
    public static final String ETCD_IS_PREFIX = "CamelEtcdIsPrefix";
    @Metadata(label = "producer", description = "The charset to use for the keys.", javaType = "String")
    public static final String ETCD_KEY_CHARSET = "CamelEtcdKeyCharset";
    @Metadata(label = "producer", description = "The charset to use for the values.", javaType = "String")
    public static final String ETCD_VALUE_CHARSET = "CamelEtcdValueCharset";

    public static final String ETCD_KEYS_ACTION_SET = "set";
    public static final String ETCD_KEYS_ACTION_DELETE = "delete";
    public static final String ETCD_KEYS_ACTION_GET = "get";

    public static final String[] ETCD_DEFAULT_ENDPOINTS = new String[] { "http://localhost:2379" };

    private Etcd3Constants() {
    }
}
