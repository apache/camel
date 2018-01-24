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
package org.apache.camel.component.wordpress.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.IntrospectionSupport;
import org.wordpress4j.model.SearchCriteria;

@UriParams
public class WordpressEndpointConfiguration extends WordpressComponentConfiguration {

    @UriParam(description = "The entity ID. Should be passed when the operation performed requires a specific entity, e.g. deleting a post", javaType = "java.lang.Integer")
    private Integer id;

    @UriParam(description = "The criteria to use with complex searches.", prefix = "criteria.", multiValue = true)
    private Map<String, Object> criteriaProperties;

    @UriParam(description = "Whether to bypass trash and force deletion.", defaultValue = "false", javaType = "java.lang.Boolean")
    private Boolean force = false;

    private SearchCriteria searchCriteria;

    public WordpressEndpointConfiguration() {
    }

    /**
     * The entity id
     */
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Boolean isForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }

    /**
     * The search criteria
     * 
     * @return
     */
    public SearchCriteria getSearchCriteria() {
        return searchCriteria;
    }

    public void setSearchCriteria(SearchCriteria searchCriteria) {
        this.searchCriteria = searchCriteria;
    }

    public Map<String, Object> getCriteriaProperties() {
        if (criteriaProperties != null) {
            return Collections.unmodifiableMap(criteriaProperties);
        }
        return null;
    }

    public void setCriteriaProperties(Map<String, Object> criteriaProperties) {
        this.criteriaProperties = Collections.unmodifiableMap(criteriaProperties);
    }

    /**
     * Return all configuration properties on a map.
     * 
     * @return
     */
    public Map<String, Object> asMap() {
        final Map<String, Object> map = new HashMap<>();
        IntrospectionSupport.getProperties(this, map, null);
        return map;
    }

}
