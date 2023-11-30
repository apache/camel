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
package org.apache.camel.component.http;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.http.common.DefaultHttpBinding;
import org.apache.camel.http.common.HttpHeaderFilterStrategy;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;

/**
 * Having custom endpoint options should not override or change any component configured options.
 */
public class HttpEndpointOptionsNotChangeComponentTest extends CamelTestSupport {

    @BindToRegistry("other")
    private MyOtherBinding binding = new MyOtherBinding();

    @BindToRegistry("myStrategy")
    private MyHeaderFilterStrategy strategy = new MyHeaderFilterStrategy();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setHttpBinding(new MyBinding());
        // must start component
        http.start();

        return context;
    }

    @Test
    public void testDoNotMessWithComponent() {
        // get default
        HttpEndpoint end = context.getEndpoint("http://www.google.com", HttpEndpoint.class);
        assertIsInstanceOf(MyBinding.class, end.getHttpBinding());

        // use a endpoint specific binding
        HttpEndpoint end2 = context.getEndpoint("http://www.google.com?httpBinding=#other", HttpEndpoint.class);
        assertIsInstanceOf(MyOtherBinding.class, end2.getHttpBinding());

        // and the default option has not been messed with
        HttpEndpoint end3 = context.getEndpoint("http://www.google.com", HttpEndpoint.class);
        assertIsInstanceOf(MyBinding.class, end3.getHttpBinding());

        // test the headerFilterStrategy
        HttpEndpoint end4 = context.getEndpoint("http://www.google.com?headerFilterStrategy=#myStrategy", HttpEndpoint.class);
        assertIsInstanceOf(MyHeaderFilterStrategy.class, end4.getHeaderFilterStrategy());
    }

    private static class MyBinding extends DefaultHttpBinding {
    }

    private static class MyOtherBinding extends DefaultHttpBinding {
    }

    private static class MyHeaderFilterStrategy extends HttpHeaderFilterStrategy {
    }

}
