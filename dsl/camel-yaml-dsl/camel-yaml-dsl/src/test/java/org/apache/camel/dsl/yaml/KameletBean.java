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
package org.apache.camel.dsl.yaml;

/**
 * A simple bean used in Kamelet loader tests to verify bean type, properties, and constructors in route template
 * definitions.
 */
class KameletBean {

    private final int id;
    private final String name;
    private final KameletBean ref;

    private String kbProp;
    private String kbProp2;
    private Object kbObjProp;

    public KameletBean() {
        this(0, null, null);
    }

    public KameletBean(int id, String name) {
        this(id, name, null);
    }

    public KameletBean(int id, String name, KameletBean ref) {
        this.id = id;
        this.name = name;
        this.ref = ref;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public KameletBean getRef() {
        return ref;
    }

    public String getKbProp() {
        return kbProp;
    }

    public void setKbProp(String kbProp) {
        this.kbProp = kbProp;
    }

    public String getKbProp2() {
        return kbProp2;
    }

    public void setKbProp2(String kbProp2) {
        this.kbProp2 = kbProp2;
    }

    public Object getKbObjProp() {
        return kbObjProp;
    }

    public void setKbObjProp(Object kbObjProp) {
        this.kbObjProp = kbObjProp;
    }
}
