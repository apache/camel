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
package org.apache.camel;

/**
 * An interface to represent an object that supports variables.
 */
public interface VariableAware {

    /**
     * Returns a variable by name.
     *
     * If the variable is of type {@link org.apache.camel.StreamCache} then the repository should ensure to reset the
     * stream cache before returning the value, to ensure the content can be read by the Camel end user and would be
     * re-readable next time.
     *
     * @param  name the name of the variable
     * @return      the value of the given variable or <tt>null</tt> if there is no variable for the given name
     */
    Object getVariable(String name);

    /**
     * Sets a variable
     *
     * @param name  of the variable
     * @param value the value of the variable
     */
    void setVariable(String name, Object value);

}
