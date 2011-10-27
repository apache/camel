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
package org.apache.camel.spring.config;

import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DefaultPackageResolverConfigureTest extends SpringTestSupport {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/config/PackageResolverTest.xml");
    }

    public void testSetAcceptableSchema() throws Exception {
        DefaultPackageScanClassResolver resolver = (DefaultPackageScanClassResolver)context.getPackageScanClassResolver();
        assertNotNull(resolver);
        // just check the accept schema
        assertTrue("We should accept the test:!", resolver.isAcceptableScheme("test://test"));
        assertTrue("We should accept the test2:!", resolver.isAcceptableScheme("test2://test"));
        
    }

}
