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
package org.apache.camel.web.resources;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.List;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Represents the list of languages available in the current camel context
 *
 * @version $Revision: 1.1 $
 */
public class LanguagesResource extends CamelChildResourceSupport {
    private static final transient Log LOG = LogFactory.getLog(LanguagesResource.class);

    public LanguagesResource(CamelContextResource contextResource) {
        super(contextResource);
    }

    public List<String> getLanguageIds() {
        return getCamelContext().getLanguageNames();
    }

    /**
     * Returns a specific language
     */
    @Path("{id}")
    public LanguageResource getLanguage(@PathParam("id") String id) {
        if (id == null) {
            return null;
        }
        return new LanguageResource(getContextResource(), id);
    }
}