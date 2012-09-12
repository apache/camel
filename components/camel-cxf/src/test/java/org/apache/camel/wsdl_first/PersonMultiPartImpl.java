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
package org.apache.camel.wsdl_first;

import javax.xml.ws.Holder;

/**
 * Test Impl class for PersonMultiPartType port type that verifies multi part SOAP message
 * functionality.
 * 
 * @version 
 */


public class PersonMultiPartImpl implements PersonMultiPartPortType {

    public void getPersonMultiPartOperation(String nameIn, int ssnIn, Holder<String> nameOut,
                                            Holder<Integer> ssnOut) {
        
        nameOut.value = "New Person Name";
        ssnOut.value = 123456789;
        
    }

}
