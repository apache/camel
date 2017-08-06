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
package org.apache.camel.component.servicenow;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.extension.MetaDataExtension;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceNowMetaDataExtensionTest extends ServiceNowTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceNowMetaDataExtensionTest.class);

    public ServiceNowMetaDataExtensionTest() {
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
    public void testMetaData() throws Exception {
        Map<String, Object> parameters = getParameters();
        parameters.put("objectType", "table");
        parameters.put("objectName", "incident");
        //parameters.put("object.sys_user.fields", "first_name,last_name");
        //parameters.put("object.incident.fields", "caller_id,severity,resolved_at,sys_id");
        //parameters.put("object.incident.fields", "^sys_.*$");
        //parameters.put("object.task.fields", "");

        MetaDataExtension.MetaData result = getExtension().meta(parameters).orElseThrow(RuntimeException::new);

        Assert.assertEquals("application/schema+json", result.getAttribute(MetaDataExtension.MetaData.CONTENT_TYPE));
        Assert.assertNotNull(result.getAttribute("date.format"));
        Assert.assertNotNull(result.getAttribute("time.format"));
        Assert.assertNotNull(result.getAttribute("date-time.format"));
        Assert.assertEquals(JsonNode.class, result.getAttribute(MetaDataExtension.MetaData.JAVA_TYPE));
        Assert.assertNotNull(result.getPayload(JsonNode.class));
        Assert.assertNotNull(result.getPayload(JsonNode.class).get("properties"));
        Assert.assertNotNull(result.getPayload(JsonNode.class).get("$schema"));
        Assert.assertEquals("http://json-schema.org/schema#", result.getPayload(JsonNode.class).get("$schema").asText());
        Assert.assertNotNull(result.getPayload(JsonNode.class).get("id"));
        Assert.assertNotNull(result.getPayload(JsonNode.class).get("type"));

        LOGGER.debug(
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result.getPayload())
        );
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInvalidObjectType() throws Exception {
        Map<String, Object> parameters = getParameters();
        parameters.put("objectType", "test");
        parameters.put("objectName", "incident");

        getExtension().meta(parameters);
    }
}
