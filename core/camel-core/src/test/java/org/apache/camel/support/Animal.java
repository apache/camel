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
package org.apache.camel.support;

public class Animal {
    private String name;
    private boolean dangerous;

    public Animal() {
    }

    public Animal(String name) {
        this.name = name;
    }

    public Animal(boolean dangerous, int foo) {
        throw new IllegalArgumentException("Should not be invoked");
    }

    public Animal(String name, boolean dangerous) {
        this.name = name;
        this.dangerous = dangerous;
    }

    public String getName() {
        return name;
    }

    public boolean isDangerous() {
        return dangerous;
    }

    public void setDangerous(boolean dangerous) {
        this.dangerous = dangerous;
    }
}
