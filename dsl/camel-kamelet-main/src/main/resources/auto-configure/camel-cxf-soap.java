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

// check if we already have CXF transport on classpath
org.apache.camel.main.download.MavenDependencyDownloader downloader = context.hasService(org.apache.camel.main.download.MavenDependencyDownloader.class);
var cxfVersion = downloader.getVersionResolver().resolve("${cxf-version}");
boolean jetty = downloader.alreadyOnClasspath("org.apache.cxf", "cxf-rt-transports-http-jetty", cxfVersion);
boolean undertow = downloader.alreadyOnClasspath("org.apache.cxf", "cxf-rt-transports-http-undertow", cxfVersion);
if (jetty || undertow){
    return null;
}

// automatic download cxf transport for undertow and register this with CXF
context.getRegistry().bind("camelCxfUndertowHttpTransportDownloader", new org.apache.cxf.transport.http.HttpDestinationFactory() {
    public org.apache.cxf.transport.http.AbstractHTTPDestination createDestination(org.apache.cxf.service.model.EndpointInfo endpointInfo, org.apache.cxf.Bus bus, org.apache.cxf.transport.http.DestinationRegistry registry) throws java.io.IOException {
        downloader.downloadDependency("org.apache.cxf", "cxf-rt-transports-http-undertow", cxfVersion);
        try {
            Class c = context.getClassResolver().resolveClass("org.apache.cxf.transport.http_undertow.UndertowHTTPServerEngineFactory");
            Object o = c.getConstructor(org.apache.cxf.Bus.class).newInstance(bus);
            Class c2 = context.getClassResolver().resolveClass("org.apache.cxf.transport.http_undertow.UndertowHTTPDestination");
            Object o2 = c2.getConstructor(org.apache.cxf.Bus.class, org.apache.cxf.transport.http.DestinationRegistry.class, org.apache.cxf.service.model.EndpointInfo.class, c)
                    .newInstance(bus, registry, endpointInfo, o);
            return (org.apache.cxf.transport.http.AbstractHTTPDestination) o2;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
});

return "Camel JBang will automatic download org.apache.cxf:cxf-rt-transports-http-undertow:" + cxfVersion + " if needed by camel-cxf-soap";