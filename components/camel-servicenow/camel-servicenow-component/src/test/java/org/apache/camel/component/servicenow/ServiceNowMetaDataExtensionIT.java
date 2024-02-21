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
package org.apache.camel.component.servicenow;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.extension.MetaDataExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "SERVICENOW_INSTANCE", matches = ".*",
                              disabledReason = "Service now instance was not provided")
public class ServiceNowMetaDataExtensionIT extends ServiceNowITSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceNowMetaDataExtensionIT.class);

    public ServiceNowMetaDataExtensionIT() {
        super(false);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected ServiceNowComponent getComponent() {
        return context().getComponent("servicenow", ServiceNowComponent.class);
    }

    protected MetaDataExtension getExtension() {
        return getComponent().getExtension(MetaDataExtension.class).orElseThrow(UnsupportedOperationException::new);
    }

    // *********************************
    //
    // *********************************

    @Test
    public void testTableMetaData() throws Exception {
        Map<String, Object> parameters = getParameters();
        parameters.put("objectType", "table");
        parameters.put("objectName", "incident");
        parameters.put("metaType", "definition");
        //parameters.put("object.sys_user.fields", "first_name,last_name");
        //parameters.put("object.incident.fields", "caller_id,severity,resolved_at,sys_id");
        //parameters.put("object.incident.fields", "^sys_.*$");
        //parameters.put("object.task.fields", "");

        MetaDataExtension.MetaData result = getExtension().meta(parameters).orElseThrow(RuntimeException::new);

        assertEquals("application/schema+json", result.getAttribute(MetaDataExtension.MetaData.CONTENT_TYPE));
        assertNotNull(result.getAttribute("date.format"));
        assertNotNull(result.getAttribute("time.format"));
        assertNotNull(result.getAttribute("date-time.format"));
        assertEquals(JsonNode.class, result.getAttribute(MetaDataExtension.MetaData.JAVA_TYPE));
        assertNotNull(result.getPayload(JsonNode.class));
        assertNotNull(result.getPayload(JsonNode.class).get("properties"));
        assertNotNull(result.getPayload(JsonNode.class).get("$schema"));
        assertEquals("http://json-schema.org/schema#", result.getPayload(JsonNode.class).get("$schema").asText());
        assertNotNull(result.getPayload(JsonNode.class).get("id"));
        assertNotNull(result.getPayload(JsonNode.class).get("type"));

        LOGGER.debug(
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result.getPayload()));
    }

    @Test
    public void testImportMetaData() throws Exception {
        Map<String, Object> parameters = getParameters();
        parameters.put("objectType", "import");
        parameters.put("metaType", "list");
        //parameters.put("object.sys_user.fields", "first_name,last_name");
        //parameters.put("object.incident.fields", "caller_id,severity,resolved_at,sys_id");
        //parameters.put("object.incident.fields", "^sys_.*$");
        //parameters.put("object.task.fields", "");

        MetaDataExtension.MetaData result = getExtension().meta(parameters).orElseThrow(RuntimeException::new);

        assertEquals("application/json", result.getAttribute(MetaDataExtension.MetaData.CONTENT_TYPE));
        assertEquals(JsonNode.class, result.getAttribute(MetaDataExtension.MetaData.JAVA_TYPE));

        LOGGER.debug(
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result.getPayload()));
    }

    @Test
    public void testInvalidObjectType() {
        Map<String, Object> parameters = getParameters();
        parameters.put("objectType", "test");
        parameters.put("objectName", "incident");

        final MetaDataExtension extension = getExtension();
        assertThrows(UnsupportedOperationException.class,
                () -> extension.meta(parameters));
    }
}
