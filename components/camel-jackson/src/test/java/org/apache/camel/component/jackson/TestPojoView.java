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
package org.apache.camel.component.jackson;

import com.fasterxml.jackson.annotation.JsonView;

public class TestPojoView {

    // START SNIPPET: jsonview
    @JsonView(Views.Age.class)
    private int age = 30;

    private int height = 190;

    @JsonView(Views.Weight.class)
    private int weight = 70;
    // END SNIPPET: jsonview

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TestPojoView that = (TestPojoView)o;

        if (age != that.age) {
            return false;
        }
        if (height != that.height) {
            return false;
        }
        return weight == that.weight;
    }

    @Override
    public int hashCode() {
        int result = age;
        result = 31 * result + height;
        result = 31 * result + weight;
        return result;
    }
}
