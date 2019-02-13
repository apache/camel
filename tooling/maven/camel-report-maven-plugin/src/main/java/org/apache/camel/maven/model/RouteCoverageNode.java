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
package org.apache.camel.maven.model;

public final class RouteCoverageNode {

    private String className;
    private String methodName;

    private String name;
    private int lineNumber;
    private int count;
    private int level;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RouteCoverageNode that = (RouteCoverageNode) o;

        if (lineNumber != that.lineNumber) {
            return false;
        }
        if (level != that.level) {
            return false;
        }
        if (!className.equals(that.className)) {
            return false;
        }
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        int result = className.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + lineNumber;
        result = 31 * result + level;
        return result;
    }

    @Override
    public String toString() {
        return "RouteCoverageNode["
            + "lineNumber=" + lineNumber
            + ", count=" + count
            + ", name='" + name + '\''
            + ", level=" + level
            + ", className='" + className + '\''
            + ']';
    }
}
