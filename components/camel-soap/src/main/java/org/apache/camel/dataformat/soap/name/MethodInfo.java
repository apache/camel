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
package org.apache.camel.dataformat.soap.name;

/**
 * Value object to hold information about a method in a JAX-WS service interface
 */
public final class MethodInfo {
    private String name;
    private String soapAction;
    private TypeInfo in;
    private TypeInfo out;

    /**
     * Initialize 
     * 
     * @param name method name
     * @param soapAction
     * @param in input parameter (document style so only one parameter)
     * @param out return type
     */
    public MethodInfo(String name, String soapAction, TypeInfo in, TypeInfo out) {
        super();
        this.name = name;
        this.soapAction = soapAction;
        this.in = in;
        this.out = out;
    }

    public String getName() {
        return name;
    }
    
    public String getSoapAction() {
        return soapAction;
    }

    public TypeInfo getIn() {
        return in;
    }

    public TypeInfo getOut() {
        return out;
    }

}
