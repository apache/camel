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
package org.apache.camel.component.yammer.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Relationships {

    private List<User> subordinates;
    private List<User> superiors;
    private List<User> colleagues;

    public List<User> getSubordinates() {
        return subordinates;
    }

    public void setSubordinates(List<User> subordinates) {
        this.subordinates = subordinates;
    }

    public List<User> getSuperiors() {
        return superiors;
    }

    public void setSuperiors(List<User> superiors) {
        this.superiors = superiors;
    }

    public List<User> getColleagues() {
        return colleagues;
    }

    public void setColleagues(List<User> colleagues) {
        this.colleagues = colleagues;
    }

    @Override
    public String toString() {
        return "Relationships [subordinates=" + subordinates + ", superiors=" + superiors + ", colleagues=" + colleagues + "]";
    }   
    
}
