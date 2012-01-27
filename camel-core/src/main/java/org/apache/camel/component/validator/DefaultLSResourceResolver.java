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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import org.apache.camel.CamelContext;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;

/**
 * Default {@link LSResourceResolver} which can included schema resources.
 */
public class DefaultLSResourceResolver implements LSResourceResolver {

    private final CamelContext camelContext;
    private final String resourcePath;

    public DefaultLSResourceResolver(CamelContext camelContext, String resourceUri) {
        this.camelContext = camelContext;
        this.resourcePath = FileUtil.onlyPath(resourceUri);
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
        return new DefaultLSInput(publicId, systemId, baseURI);
    }
    
    private final class DefaultLSInput implements LSInput {
        
        private final String publicId;
        private final String systemId;
        private final String baseURI;
        private final String uri;

        private DefaultLSInput(String publicId, String systemId, String baseURI) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.baseURI = baseURI;

            if (resourcePath != null) {
                uri = resourcePath + "/" + systemId;
            } else {
                uri = systemId;
            }
        }

        @Override
        public Reader getCharacterStream() {
            InputStream is = getByteStream();
            return camelContext.getTypeConverter().convertTo(Reader.class, is);
        }

        @Override
        public void setCharacterStream(Reader reader) {
            // noop
        }

        @Override
        public InputStream getByteStream() {
            try {
                return ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext.getClassResolver(), uri);
            } catch (IOException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        @Override
        public void setByteStream(InputStream inputStream) {
            // noop
        }

        @Override
        public String getStringData() {
            InputStream is = getByteStream();
            return camelContext.getTypeConverter().convertTo(String.class, is);
        }

        @Override
        public void setStringData(String stringData) {
            // noop
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setSystemId(String systemId) {
            // noop
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public void setPublicId(String publicId) {
            // noop
        }

        @Override
        public String getBaseURI() {
            return baseURI;
        }

        @Override
        public void setBaseURI(String baseURI) {
            // noop
        }

        @Override
        public String getEncoding() {
            return null;
        }

        @Override
        public void setEncoding(String encoding) {
            // noop
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
            // noop
        }

        @Override
        public String toString() {
            return "DefaultLSInput[" + uri + "]";
        }
    }
    
}
