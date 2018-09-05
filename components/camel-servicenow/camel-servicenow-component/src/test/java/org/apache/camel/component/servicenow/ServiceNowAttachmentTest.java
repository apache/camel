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

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servicenow.model.AttachmentMeta;
import org.junit.Test;

import static org.apache.camel.util.ResourceHelper.resolveResourceAsInputStream;

public class ServiceNowAttachmentTest extends ServiceNowTestSupport {
    @Produce(uri = "direct:servicenow")
    ProducerTemplate template;

    @Test
    public void testAttachment() throws Exception {
        List<AttachmentMeta> attachmentMetaList = template.requestBodyAndHeaders(
            "direct:servicenow",
            null,
            kvBuilder()
                .put(ServiceNowConstants.RESOURCE, ServiceNowConstants.RESOURCE_ATTACHMENT)
                .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                .put(ServiceNowConstants.MODEL, AttachmentMeta.class)
                .put(ServiceNowParams.SYSPARM_QUERY, "content_type=application/octet-stream")
                .put(ServiceNowParams.SYSPARM_LIMIT, 1)
                .build(),
            List.class
        );

        assertFalse(attachmentMetaList.isEmpty());

        Exchange getExistingResult = template.send(
            "direct:servicenow",
            e -> {
                e.getIn().setHeader(ServiceNowConstants.RESOURCE, ServiceNowConstants.RESOURCE_ATTACHMENT);
                e.getIn().setHeader(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_CONTENT);
                e.getIn().setHeader(ServiceNowParams.PARAM_SYS_ID.getHeader(), attachmentMetaList.get(0).getId());
            }
        );

        assertNotNull(getExistingResult.getIn().getHeader(ServiceNowConstants.CONTENT_META));
        assertNotNull(getExistingResult.getIn().getBody());
        assertTrue(getExistingResult.getIn().getBody() instanceof InputStream);

        Map<String, String> contentMeta = getExistingResult.getIn().getHeader(ServiceNowConstants.CONTENT_META, Map.class);
        assertEquals(contentMeta.get("file_name"), attachmentMetaList.get(0).getFileName());
        assertEquals(contentMeta.get("table_name"), attachmentMetaList.get(0).getTableName());
        assertEquals(contentMeta.get("sys_id"), attachmentMetaList.get(0).getId());

        Exchange putResult = template.send(
            "direct:servicenow",
            e -> {
                e.getIn().setHeader(ServiceNowConstants.RESOURCE, ServiceNowConstants.RESOURCE_ATTACHMENT);
                e.getIn().setHeader(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_UPLOAD);
                e.getIn().setHeader(ServiceNowConstants.MODEL, AttachmentMeta.class);
                e.getIn().setHeader(ServiceNowConstants.CONTENT_TYPE, "application/octet-stream");
                e.getIn().setHeader(ServiceNowParams.PARAM_FILE_NAME.getHeader(), UUID.randomUUID().toString());
                e.getIn().setHeader(ServiceNowParams.PARAM_TABLE_NAME.getHeader(), attachmentMetaList.get(0).getTableName());
                e.getIn().setHeader(ServiceNowParams.PARAM_TABLE_SYS_ID.getHeader(), attachmentMetaList.get(0).getTableSysId());
                e.getIn().setBody(resolveResourceAsInputStream(e.getContext().getClassResolver(), "classpath:my-content.txt"));
            }
        );

        Exchange getCreatedResult = template.send(
            "direct:servicenow",
            e -> {
                e.getIn().setHeader(ServiceNowConstants.RESOURCE, ServiceNowConstants.RESOURCE_ATTACHMENT);
                e.getIn().setHeader(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_CONTENT);
                e.getIn().setHeader(ServiceNowParams.PARAM_SYS_ID.getHeader(), putResult.getIn().getBody(AttachmentMeta.class).getId());
            }
        );

        assertNotNull(getCreatedResult.getIn().getHeader(ServiceNowConstants.CONTENT_META));
        assertNotNull(getCreatedResult.getIn().getBody());
        assertTrue(getCreatedResult.getIn().getBody() instanceof InputStream);

        Exchange deleteResult = template.send(
            "direct:servicenow",
            e -> {
                e.getIn().setHeader(ServiceNowConstants.RESOURCE, ServiceNowConstants.RESOURCE_ATTACHMENT);
                e.getIn().setHeader(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_DELETE);
                e.getIn().setHeader(ServiceNowParams.PARAM_SYS_ID.getHeader(), putResult.getIn().getBody(AttachmentMeta.class).getId());
            }
        );

        if (deleteResult.getException() != null) {
            throw deleteResult.getException();
        }
    }

    // *************************************************************************
    //
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:servicenow")
                    .to("servicenow:{{env:SERVICENOW_INSTANCE}}")
                    .to("log:org.apache.camel.component.servicenow?level=INFO&showAll=true")
                    .to("mock:servicenow");
            }
        };
    }
}
