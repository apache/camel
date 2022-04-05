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
package org.apache.camel.component.etcd;

import org.apache.camel.spi.Metadata;

public interface EtcdConstants {

    // The schemes
    String SCHEME_KEYS = "etcd-keys";
    String SCHEME_STATS = "etcd-stats";
    String SCHEME_WATCH = "etcd-watch";

    String ETCD_DEFAULT_URIS = "http://localhost:2379,http://localhost:4001";

    @Metadata(label = "producer", description = "The action to perform.\n" +
                                                "Supported values:\n" +
                                                "\n" +
                                                "* set\n" +
                                                "* get\n" +
                                                "* delete\n",
              javaType = "String", applicableFor = SCHEME_KEYS)
    String ETCD_ACTION = "CamelEtcdAction";
    @Metadata(description = "The namespace", javaType = "String")
    String ETCD_NAMESPACE = "CamelEtcdNamespace";
    @Metadata(description = "The target path", javaType = "String")
    String ETCD_PATH = "CamelEtcdPath";
    @Metadata(label = "producer", description = "The timeout of the request in milliseconds", javaType = "Long or Boolean",
              applicableFor = { SCHEME_KEYS, SCHEME_WATCH })
    String ETCD_TIMEOUT = "CamelEtcdTimeout";
    @Metadata(label = "producer", description = "To apply an action recursively.", javaType = "Boolean",
              applicableFor = SCHEME_KEYS)
    String ETCD_RECURSIVE = "CamelEtcdRecursive";
    @Metadata(label = "producer", description = "To set the lifespan of a key in milliseconds.", javaType = "Integer",
              applicableFor = SCHEME_KEYS)
    String ETCD_TTL = "CamelEtcdTtl";

    String ETCD_KEYS_ACTION_SET = "set";
    String ETCD_KEYS_ACTION_DELETE = "delete";
    String ETCD_KEYS_ACTION_DELETE_DIR = "deleteDir";
    String ETCD_KEYS_ACTION_GET = "get";

    String ETCD_LEADER_STATS_PATH = "/leader";
    String ETCD_SELF_STATS_PATH = "/self";
    String ETCD_STORE_STATS_PATH = "/store";
}
