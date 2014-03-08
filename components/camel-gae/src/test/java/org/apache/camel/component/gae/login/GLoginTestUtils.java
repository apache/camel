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
package org.apache.camel.component.gae.login;

import com.google.appengine.api.mail.MailService.Message;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;

public final class GLoginTestUtils {

    private static CamelContext context;
    private static GLoginComponent component;

    static {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("customOutboundBinding", new GLoginBinding() { });  // subclass
        context = new DefaultCamelContext(registry);
        component = new GLoginComponent();
        component.setCamelContext(context);
    }

    private GLoginTestUtils() {
    }
    
    public static CamelContext getCamelContext() {
        return context;
    }
    
    public static GLoginEndpoint createEndpoint(String endpointUri) throws Exception {
        return (GLoginEndpoint)component.createEndpoint(endpointUri);
    }
    
    public static Message createMessage() throws Exception {
        return new Message();
    }
    
}
