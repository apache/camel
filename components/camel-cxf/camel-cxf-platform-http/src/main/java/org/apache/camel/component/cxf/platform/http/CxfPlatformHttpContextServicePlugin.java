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
package org.apache.camel.component.cxf.platform.http;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ContextServicePlugin;

/**
 * Switches camel-cxf-platform-http on in a {@link CamelContext}, found on the classpath ({@code META-INF/services}):
 * binds the {@link CxfPlatformHttpTransport}, so the cxf consumers of the context are served on its platform-http
 * server. The transport reads its options, {@code camel.cxf.path} (default {@code /services}) and
 * {@code camel.cxf.useXForwardedHeaders} (default false), when the first service starts.
 * <p/>
 * Camel JBang binds the transport itself (with a starter of its platform-http server); then the plugin does nothing.
 */
public class CxfPlatformHttpContextServicePlugin implements ContextServicePlugin {

    @Override
    public void load(CamelContext camelContext) {
        if (camelContext.getRegistry().lookupByNameAndType(CxfPlatformHttpTransport.BEAN_NAME,
                CxfPlatformHttpTransport.class)
            == null) {
            CxfPlatformHttpTransport transport = new CxfPlatformHttpTransport();
            transport.setCamelContext(camelContext);
            camelContext.getRegistry().bind(CxfPlatformHttpTransport.BEAN_NAME, transport);
        }
    }
}
