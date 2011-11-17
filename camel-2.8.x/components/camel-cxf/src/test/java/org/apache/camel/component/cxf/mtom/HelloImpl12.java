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
package org.apache.camel.component.cxf.mtom;

import javax.jws.WebService;
import javax.xml.bind.annotation.XmlSeeAlso;

import org.apache.camel.cxf.mtom_feature.Hello;

/**
 * Hello Test Impl class for SOAP 1.2
 * @version 
 */

@WebService(serviceName = "HelloService12")
@XmlSeeAlso({org.apache.camel.cxf.mtom_feature.types.ObjectFactory.class})

@javax.xml.ws.BindingType(value = javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_MTOM_BINDING)  
public class HelloImpl12 extends HelloImpl implements Hello {
        

}
