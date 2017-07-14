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
package org.apache.camel.component.http;

import org.apache.camel.CamelContext;
import org.apache.camel.http.common.DefaultHttpBinding;
import org.apache.camel.http.common.HttpHeaderFilterStrategy;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Having custom endpoint options should not override or change any component configured options.
 *
 */
public class HttpEndpointOptionsNotChangeComponentTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setHttpBinding(new MyBinding());

        return context;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("other", new MyOtherBinding());
        jndi.bind("myStrategy", new MyHeaderFilterStrategy());
        return jndi;
    }

    @Test
    public void testDoNotMessWithComponent() throws Exception {
        // get default
        HttpEndpoint end = context.getEndpoint("http://www.google.com", HttpEndpoint.class);
        assertIsInstanceOf(MyBinding.class, end.getBinding());

        // use a endpoint specific binding
        HttpEndpoint end2 = context.getEndpoint("http://www.google.com?httpBinding=#other", HttpEndpoint.class);
        assertIsInstanceOf(MyOtherBinding.class, end2.getBinding());

        // and the default option has not been messed with
        HttpEndpoint end3 = context.getEndpoint("http://www.google.com", HttpEndpoint.class);
        assertIsInstanceOf(MyBinding.class, end3.getBinding());
        
        // test the headerFilterStrategy
        HttpEndpoint end4 = context.getEndpoint("http://www.google.com?headerFilterStrategy=#myStrategy", HttpEndpoint.class);
        assertIsInstanceOf(MyHeaderFilterStrategy.class, end4.getHeaderFilterStrategy());
    }

    private static class MyBinding extends DefaultHttpBinding {
        MyBinding() {
            super(new HttpEndpoint());
        }
    }

    private static class MyOtherBinding extends DefaultHttpBinding {
        MyOtherBinding() {
            super(new HttpEndpoint());
        }
    }
    
    private static class MyHeaderFilterStrategy extends HttpHeaderFilterStrategy {
    }

}
