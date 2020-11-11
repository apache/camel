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
package org.apache.camel.tooling.util.srcgen;

public class Param {

    GenericType type;
    String typeLiteral;
    String name;
    boolean vararg;

    public Param(GenericType type, String name) {
        this(type, name, false);
    }

    public Param(String type, String name) {
        this(type, name, false);
    }

    public Param(GenericType type, String name, boolean vararg) {
        this.type = type;
        this.name = name;
        this.vararg = vararg;
    }

    public Param(String type, String name, boolean vararg) {
        this.typeLiteral = type;
        this.name = name;
        this.vararg = vararg;
    }

    public GenericType getType() {
        return type;
    }

    public String getTypeLiteral() {
        return typeLiteral;
    }

    public String getName() {
        return name;
    }

    public boolean isVararg() {
        return vararg;
    }
}
