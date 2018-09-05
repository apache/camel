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
package org.apache.camel.parser.model;

public final class CamelNodeDetailsFactory {

    private int order;

    private CamelNodeDetailsFactory() {
    }

    public static CamelNodeDetailsFactory newInstance() {
        return new CamelNodeDetailsFactory();
    }

    public CamelNodeDetails newNode(CamelNodeDetails parent, String name) {
        return new CamelNodeDetails(parent, name, ++order);
    }

    public CamelNodeDetails copyNode(CamelNodeDetails parent, String name, CamelNodeDetails copoy) {
        return new CamelNodeDetails(parent, name, ++order, copoy);
    }
}
