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
package org.apache.camel.component.cxf.util;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.camel.component.cxf.CxfConstants;
import org.apache.cxf.common.classloader.ClassLoaderUtils;

public final class UriUtils {

    private UriUtils() {
        // not constructred
    }
    
    static URL getWsdlUrl(final URI uri) throws MalformedURLException {
        URL wsdlUrl = null;
        
        if (uri.getScheme().equals(CxfConstants.PROTOCOL_NAME_RES)) {       
            if (uri.getPath() != null) {
                String path = uri.isAbsolute() ? getRelativePath(uri) : uri.getPath();
                wsdlUrl = ClassLoaderUtils.getResource(path, UriUtils.class);
            }
        } else {
            wsdlUrl = new URL(uri.getScheme(), uri.getHost(), uri.getPort(), uri.getPath());
        }

        return wsdlUrl;
    }

    private static String getRelativePath(URI uri) {
        URI base = null;
        try {
            base = new URI(CxfConstants.PROTOCOL_NAME_RES, "", "/", "");
        } catch (URISyntaxException e) {
            // this shouldn't fail
            e.printStackTrace();
        }
        return base.relativize(uri).getPath();
    }
}

