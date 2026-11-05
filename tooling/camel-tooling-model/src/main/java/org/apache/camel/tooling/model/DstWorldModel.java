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
package org.apache.camel.tooling.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A fake world of a component for deterministic simulation tests (camel-test-dst), as declared with {@code @DstWorld}
 * on the world class in the test sources of the component (CAMEL-26438).
 *
 * @since 4.24
 */
public class DstWorldModel {

    private String name;
    private String javaType;
    private String fake;
    private List<String> covers = new ArrayList<>();
    private List<String> faults = new ArrayList<>();
    private List<String> invariants = new ArrayList<>();
    private List<String> issues = new ArrayList<>();
    private List<String> tests = new ArrayList<>();

    /** The simple name of the world class, for example {@code SqsWorld}. */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** The fully qualified name of the world class (a test class: it is not in the component JAR). */
    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    /** The fake of the external system: the class name of the fake, or how the client is faked. */
    public String getFake() {
        return fake;
    }

    public void setFake(String fake) {
        this.fake = fake;
    }

    /** What the world exercises. */
    public List<String> getCovers() {
        return covers;
    }

    public void setCovers(List<String> covers) {
        this.covers = covers;
    }

    /** The faults the world injects. */
    public List<String> getFaults() {
        return faults;
    }

    public void setFaults(List<String> faults) {
        this.faults = faults;
    }

    /** The invariants checked at the end of each run. */
    public List<String> getInvariants() {
        return invariants;
    }

    public void setInvariants(List<String> invariants) {
        this.invariants = invariants;
    }

    /** The JIRA issues of the world. */
    public List<String> getIssues() {
        return issues;
    }

    public void setIssues(List<String> issues) {
        this.issues = issues;
    }

    /** The simple names of the DST test classes that run the world. */
    public List<String> getTests() {
        return tests;
    }

    public void setTests(List<String> tests) {
        this.tests = tests;
    }
}
