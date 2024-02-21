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
package org.apache.camel.component.jcr;

import org.apache.camel.spi.Metadata;

/**
 * JCR Constants.
 */
public final class JcrConstants {

    /**
     * Property key for specifying the name of a node in the repository
     */
    public static final String JCR_INSERT = "CamelJcrInsert";
    public static final String JCR_GET_BY_ID = "CamelJcrGetById";
    @Metadata(label = "producer", description = "The name of the target node", javaType = "String",
              defaultValue = "The exchange id")
    public static final String JCR_NODE_NAME = "CamelJcrNodeName";
    @Metadata(label = "producer",
              description = "The operation to perform. Possible values: " + JCR_INSERT + " or " + JCR_GET_BY_ID,
              javaType = "String", defaultValue = JCR_INSERT)
    public static final String JCR_OPERATION = "CamelJcrOperation";
    @Metadata(label = "producer", description = "The node type of the target node", javaType = "String")
    public static final String JCR_NODE_TYPE = "CamelJcrNodeType";

    private JcrConstants() {
        // Utility class
    }
}
