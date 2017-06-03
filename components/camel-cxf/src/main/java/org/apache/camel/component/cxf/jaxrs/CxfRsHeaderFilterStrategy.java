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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;

/**
 *
 * @version 
 */
public class CxfRsHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    public CxfRsHeaderFilterStrategy() {
        initialize();  
    }

    protected void initialize() {
        
        getOutFilter().add(CxfConstants.OPERATION_NAME.toLowerCase());
        
        getOutFilter().add("Content-Type".toLowerCase());
        // Support to filter the Content-Type case insensitive
        setLowerCase(true);
        
        // filter headers begin with "Camel" or "org.apache.camel"
        setOutFilterPattern("(Camel|org\\.apache\\.camel)[\\.|a-z|A-z|0-9]*");

    }

}
