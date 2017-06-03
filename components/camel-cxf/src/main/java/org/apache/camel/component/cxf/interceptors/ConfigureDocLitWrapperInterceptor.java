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
package org.apache.camel.component.cxf.interceptors;

import org.apache.cxf.interceptor.AbstractInDatabindingInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;

/**
 * This interface configures the flag {@link DocLiteralInInterceptor#KEEP_PARAMETERS_WRAPPER}.
 * 
 * @version 
 */
public class ConfigureDocLitWrapperInterceptor extends AbstractInDatabindingInterceptor {

    boolean unwrapParameterFlag;
    
    public ConfigureDocLitWrapperInterceptor(boolean unwrapParameterFlag) {
        super(Phase.UNMARSHAL);        
        addBefore("org.apache.cxf.interceptor.DocLiteralInInterceptor");
        addBefore("org.apache.cxf.wsdl.interceptors.DocLiteralInInterceptor");
        this.unwrapParameterFlag = unwrapParameterFlag;
    }

    public void handleMessage(Message message) throws Fault {
        message.put("org.apache.cxf.interceptor.DocLiteralInInterceptor.DocLiteralInInterceptor.keep-parameters-wrapper", unwrapParameterFlag);
        message.put("org.apache.cxf.wsdl.interceptors.DocLiteralInInterceptor.DocLiteralInInterceptor.keep-parameters-wrapper", unwrapParameterFlag);
    }
    
    public boolean isUnwrapParameterFlag() {
        return unwrapParameterFlag;
    }

    public void setUnwrapParameterFlag(boolean unwrapParameterFlag) {
        this.unwrapParameterFlag = unwrapParameterFlag;
    }

}
