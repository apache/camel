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

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.processor.validation.ValidatingProcessor;

import org.springframework.core.io.Resource;

/**
 * @version $Revision: $
 */
public class SpringValidator extends ValidatingProcessor {
    private Resource schemaResource;

    public Resource getSchemaResource() {
        return schemaResource;
    }

    public void setSchemaResource(Resource schemaResource) {
        this.schemaResource = schemaResource;
    }

    @Override
    protected Source createSchemaSource() throws IOException {
        if (schemaResource != null) {
            if (schemaResource.getURL() == null) {
                return new StreamSource(schemaResource.getInputStream());
            } else {
                return new StreamSource(schemaResource.getInputStream(), schemaResource.getURL().toExternalForm());
            }
        } else {
            throw new IllegalArgumentException("You must specify a schema, schemaFile, schemaResource, schemaSource or schemaUrl property");
        }
    }
}
