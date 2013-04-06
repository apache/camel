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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;

/**
 * Value object to hold information about a method in a JAX-WS service interface.
 * Method can have many parameters in the signature, but only one response object.
 */
public final class MethodInfo {
    private String name;
    private String soapAction;
    private TypeInfo[] in;
    private TypeInfo out;
    
    // map of type name to qname
    private Map<String, TypeInfo> inTypeMap;

    /**
     * Initialize 
     * 
     * @param name method name
     * @param soapAction
     * @param in input parameters
     * @param out return types
     */
    public MethodInfo(String name, String soapAction, TypeInfo[] in, TypeInfo out) {
        this.name = name;
        this.soapAction = soapAction;
        this.in = in;
        this.out = out;
        
        this.inTypeMap = new HashMap<String, TypeInfo>();
        for (TypeInfo typeInfo : in) {
            if (inTypeMap.containsKey(typeInfo.getTypeName())
                && (!(typeInfo.getTypeName().equals("javax.xml.ws.Holder")))
                && (!(inTypeMap.get(typeInfo.getTypeName()).equals(typeInfo.getElName())))) {
                throw new RuntimeCamelException("Ambiguous QName mapping. The type [ "
                                                  + typeInfo.getTypeName()
                                                  + " ] is already mapped to a QName in this method."
                                                  + " This is not supported.");                   
            }
            inTypeMap.put(typeInfo.getTypeName(), typeInfo);
        }
    }

    public String getName() {
        return name;
    }
    
    public String getSoapAction() {
        return soapAction;
    }

    public TypeInfo[] getIn() {
        return in;
    }

    public TypeInfo getOut() {
        return out;
    }
     
    public TypeInfo getIn(String typeName) {
        return this.inTypeMap.get(typeName);
    }
    
}
