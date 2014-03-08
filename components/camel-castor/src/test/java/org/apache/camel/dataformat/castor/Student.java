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
package org.apache.camel.dataformat.castor;

public class Student {
    private String stuFirstName;
    private String stuLastName;
    private Integer stuAge;

    public Integer getStuAge() {
        return stuAge;
    }

    public void setStuAge(Integer stuAge) {
        this.stuAge = stuAge;
    }

    public String getStuFirstName() {
        return stuFirstName;
    }

    public void setStuFirstName(String stuFirstName) {
        this.stuFirstName = stuFirstName;
    }

    public String getStuLastName() {
        return stuLastName;
    }

    public void setStuLastName(String stuLastName) {
        this.stuLastName = stuLastName;
    }

    @Override
    public String toString() {
        return "Name : " + getStuLastName()
                + ", " + getStuFirstName()
                + " [" + stuAge + "]";
    }

    @Override
    public boolean equals(Object obj) {
        try {
            Student student = (Student) obj;
            return stuLastName.equals(student.stuLastName)
                    && stuFirstName.equals(student.stuFirstName)
                    && stuAge.equals(student.stuAge);
        } catch (ClassCastException e) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
