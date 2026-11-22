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

// a cxf consumer is served on the platform-http server (camel-cxf-platform-http), as the CXF extension of Quarkus does
// on the Vert.x server of Quarkus; the address of a consumer is a path, served under /services (camel.cxf.path)
//
// only the Camel API is used directly: the classes of the Camel CLI are not visible to the compiler of this script
// when it runs from the launcher, so they are called by reflection
var resolver = context.getClassResolver();
Class downloaderType = resolver.resolveMandatoryClass("org.apache.camel.main.download.DependencyDownloader");
Object downloader = context.hasService(downloaderType);
downloaderType.getMethod("downloadHiddenDependency", String.class, String.class, String.class)
        .invoke(downloader, "org.apache.camel", "camel-cxf-platform-http", context.getVersion());

Class transportType = resolver.resolveMandatoryClass("org.apache.camel.component.cxf.platform.http.CxfPlatformHttpTransport");
Object transport = context.getRegistry().lookupByName("camelCxfPlatformHttpTransport");
if (transport == null) {
    transport = transportType.getConstructor().newInstance();
    transportType.getMethod("setCamelContext", org.apache.camel.CamelContext.class).invoke(transport, context);
    context.getRegistry().bind("camelCxfPlatformHttpTransport", transport);
}

// start the platform-http server when the first cxf consumer starts (not for a cxf producer only)
java.lang.reflect.Method setup = resolver.resolveMandatoryClass("org.apache.camel.main.download.MainHttpServerFactory")
        .getMethod("setupHttpServer", org.apache.camel.CamelContext.class, boolean.class);
Runnable starter = () -> {
    try {
        setup.invoke(null, context, false);
    } catch (Exception e) {
        throw new IllegalStateException("Cannot start the platform-http server for the cxf consumers", e);
    }
};
transportType.getMethod("setHttpServerStarter", Runnable.class).invoke(transport, starter);

return "Camel CLI serves cxf consumers on the platform-http server under "
       + context.resolvePropertyPlaceholders("{{camel.cxf.path:/services}}");
