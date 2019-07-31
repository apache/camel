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
package org.apache.camel.tests.partialclasspath;

import org.apache.camel.util.ObjectHelper;

public class MyBean {
    private String a;
    private String b;

    public MyBean() {
    }

    public MyBean(String a, String b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MyBean) {
            MyBean that = (MyBean) o;
            return ObjectHelper.equal(this.a, that.a) && ObjectHelper.equal(this.b, that.b);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int answer = 1;
        if (a != null) {
            answer += a.hashCode() * 37;
        }
        if (b != null) {
            answer += b.hashCode() * 37;
        }
        return answer;
    }

    @Override
    public String toString() {
        return "MyBean[a=" + a + " b=" + b + "]";
    }

    public String getA() {
        return a;
    }

    public void setA(String a) {
        this.a = a;
    }

    public String getB() {
        return b;
    }

    public void setB(String b) {
        this.b = b;
    }
}
