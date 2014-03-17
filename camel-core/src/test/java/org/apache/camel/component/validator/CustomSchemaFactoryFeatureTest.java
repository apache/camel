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
package org.apache.camel.component.validator;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.impl.JndiRegistry;

public class CustomSchemaFactoryFeatureTest extends ContextTestSupport {
    // Need to bind the CustomerSchemaFactory
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        SchemaFactory mySchemaFactory =  SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        mySchemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        registry.bind("MySchemaFactory", mySchemaFactory);
        return registry;
    }
    
    
    // just inject the SchemaFactory as we want
    public void testCustomSchemaFactory() throws Exception {
        ValidatorComponent v = new ValidatorComponent();
        v.setCamelContext(context);
        v.createEndpoint("validator:org/apache/camel/component/validator/unsecuredSchema.xsd?schemaFactory=#MySchemaFactory");
       
        try {
            v.createEndpoint("validator:org/apache/camel/component/validator/unsecuredSchema.xsd");
            // we should get an security exception in JDK 7 with Oracle or Sun JDK
            String jdkVendor = System.getProperty("java.vm.vendor");
            if (jdkVendor != null && (jdkVendor.indexOf("Oracle") > 0 || jdkVendor.indexOf("Sun") > 0)) {
                fail("Expect exception here");
            }
        } catch (Exception ex) {
            // do nothing here
        }
    }
    
    

}
