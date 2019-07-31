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
package org.apache.camel.dataformat.xmlsecurity;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class SpringXmlSecurityDataFormatWithKeyPasswordTest implements CamelContextAware {
    
    CamelContext camelContext;
    
    TestHelper testHelper = new TestHelper();
    
   
    @Test
    public void testPartialPayloadAsymmetricKeyDecryptionCustomNS() throws Exception {
        testHelper.testDecryption(TestHelper.NS_XML_FRAGMENT, camelContext);
    }


    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }


    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
    
}
