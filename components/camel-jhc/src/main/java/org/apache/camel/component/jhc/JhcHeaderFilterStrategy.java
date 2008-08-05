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
package org.apache.camel.component.jhc;

import org.apache.camel.impl.DefaultHeaderFilterStrategy;

/**
 * 
 * @version $Revision$
 */
public class JhcHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    public JhcHeaderFilterStrategy() {
        initialize();
    }
    
    protected void initialize() {
        // We could import filters from http component but that also means
        // a new dependency on camel-http
        getOutFilter().add("content-length");
        getOutFilter().add("content-type");
        getOutFilter().add(JhcProducer.HTTP_RESPONSE_CODE);
        setIsLowercase(true);

        // filter headers begin with "org.apache.camel"
        setOutFilterPattern("(org\\.apache\\.camel)[\\.|a-z|A-z|0-9]*");   
    }
}
