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
package org.apache.camel.processor.validation;

import java.io.InputStream;
import java.io.Reader;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import org.xml.sax.InputSource;

import org.apache.xml.resolver.tools.CatalogResolver;

public class CatalogLSResourceResolver implements LSResourceResolver {

    CatalogResolver catalogResolver;

    public CatalogLSResourceResolver() {
    }

    public CatalogLSResourceResolver(CatalogResolver catalogResolver) {
        this.catalogResolver = catalogResolver;
    }

    public CatalogResolver getCatalogResolver() {
        return catalogResolver;
    }

    public void setCatalogResolver(CatalogResolver catalogResolver) {
        this.catalogResolver = catalogResolver;
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
        return new LSInputSource(namespaceURI, publicId, systemId, baseURI);
    }

    class LSInputSource implements LSInput {
        private InputSource inputSource;
        private String publicId;
        private String systemId;
        private String baseURI;

        LSInputSource(String namespaceURI, String publicId, String systemId, String baseURI) {
            if (publicId == null) {
                publicId = namespaceURI;
            }

            this.publicId = publicId;
            this.systemId = systemId;
            this.baseURI = baseURI;

            if (catalogResolver == null) {
                throw new IllegalStateException("catalogResolver must be provided");
            }

            this.inputSource = catalogResolver.resolveEntity(publicId, systemId);
        }

        @Override
        public Reader getCharacterStream() {
            return null;
        }

        @Override
        public void setCharacterStream(Reader characterStream) {
            // noop
        }

        @Override
        public InputStream getByteStream() {
            return inputSource != null ? inputSource.getByteStream() : null;
        }

        @Override
        public void setByteStream(InputStream byteStream) {
            if (inputSource != null) {
                inputSource.setByteStream(byteStream);
            }
        }

        @Override
        public String getStringData() {
            return null;
        }

        @Override
        public void setStringData(String stringData) {
            // noop
        }

        @Override
        public String getSystemId() {
            if (inputSource != null) {
                return inputSource.getSystemId();
            }

            return systemId;
        }

        @Override
        public void setSystemId(String systemId) {
            if (inputSource != null) {
                inputSource.setSystemId(systemId);
            }
        }

        @Override
        public String getPublicId() {
            if (inputSource != null) {
                return inputSource.getPublicId();
            }

            return publicId;
        }

        @Override
        public void setPublicId(String publicId) {
            if (inputSource != null) {
                inputSource.setPublicId(publicId);
            } else {
                this.publicId = publicId;
            }
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
            if (inputSource != null) {
                return inputSource.getEncoding();
            }

            return null;
        }

        @Override
        public void setEncoding(String encoding) {
            if (inputSource != null) {
                inputSource.setEncoding(encoding);
            }
        }

        @Override
        public boolean getCertifiedText() {
            return true;
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
            // noop
        }
    }
}
