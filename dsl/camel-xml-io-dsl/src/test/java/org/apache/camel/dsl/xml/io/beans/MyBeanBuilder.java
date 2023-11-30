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
package org.apache.camel.dsl.xml.io.beans;

public class MyBeanBuilder {

    private String field1;
    private String field2;
    private int age;

    public MyBeanBuilder field1(String field1) {
        this.field1 = field1;
        return this;
    }

    public MyBeanBuilder field2(String field2) {
        this.field2 = field2;
        return this;
    }

    public MyBeanBuilder age(int age) {
        this.age = age;
        return this;
    }

    public MyBean createTheBean() {
        return new MyBean(field1, field2, age);
    }

}
