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
package org.apache.camel.component.javaspace;

import net.jini.core.entry.Entry;

/**
 * Title: Plain JavaSpaces Example - TestEntry entry class.
 * <p>
 * Description: Simple entry implementation to be used in write, take and read
 * space operations.
 */
public class TestEntry implements Entry {
    private static final long serialVersionUID = 1L;
    
    public String content;
    public Integer id;

    /**
     * No arguments constructor (mandatory in an entry class).
     */
    public TestEntry() {
    }
    
    /**
     * @return a string containing the message attributes for display.
     */
    public String toString() {
        return "TestEntry ID:" + id + " ,TestEntry content: " + content;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
