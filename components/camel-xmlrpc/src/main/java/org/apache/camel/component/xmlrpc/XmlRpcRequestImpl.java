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
package org.apache.camel.component.xmlrpc;

import java.util.List;

import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.XmlRpcRequestConfig;

public class XmlRpcRequestImpl implements XmlRpcRequest {
    private static final Object[] ZERO_PARAMS = new Object[0];
    private final String methodName;
    private final Object[] params;
    
    public XmlRpcRequestImpl(String pMethodName, Object[] pParams) {
        methodName = pMethodName;
        if (methodName == null) {
            throw new NullPointerException("The method name must not be null.");
        }
        params = pParams == null ? ZERO_PARAMS : pParams;
    }
    
    public XmlRpcRequestImpl(String pMethodName, List<?> pParams) {
        this(pMethodName, pParams == null ? null : pParams.toArray());
    }

    // we don't want to use the config here
    public XmlRpcRequestConfig getConfig() {
        return null;
    }

    public String getMethodName() {
        return methodName;
    }

    public int getParameterCount() {
        return params.length;
    }

    public Object getParameter(int pIndex) {
        return params[pIndex];
    }
   

}
