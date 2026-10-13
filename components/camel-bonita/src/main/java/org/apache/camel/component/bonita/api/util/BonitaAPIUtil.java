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
package org.apache.camel.component.bonita.api.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;

import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import org.apache.camel.component.bonita.api.filter.BonitaAuthFilter;
import org.apache.camel.component.bonita.api.filter.JsonClientFilter;
import org.apache.camel.component.bonita.api.model.FileInput;
import org.apache.camel.component.bonita.api.model.ProcessDefinitionResponse;
import org.apache.camel.component.bonita.api.model.UploadFileResponse;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.AttachmentBuilder;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;

import static jakarta.ws.rs.client.Entity.entity;

public class BonitaAPIUtil {

    private WebTarget webTarget;

    /**
     * Creates a helper that uploads to the Bonita server of the given configuration, with the account of that
     * configuration. Every call creates a new helper with a JAX-RS client of its own; nothing is shared between
     * configurations. {@code BonitaAPIBuilder} does not use this method: it binds the helper to the client of the
     * {@code BonitaAPI} that it builds, see {@link #forClient(Client, BonitaAPIConfig)}.
     */
    public static BonitaAPIUtil getInstance(BonitaAPIConfig bonitaAPIConfig) {
        ClientBuilder clientBuilder = ClientBuilder.newBuilder();
        Client client = clientBuilder.build();
        client.register(JacksonJsonProvider.class);
        client.register(new BonitaAuthFilter(bonitaAPIConfig));
        return forClient(client, bonitaAPIConfig);
    }

    /**
     * Creates a helper that uploads with the given client, which must already authenticate against the Bonita server of
     * the given configuration.
     */
    public static BonitaAPIUtil forClient(Client client, BonitaAPIConfig bonitaAPIConfig) {
        BonitaAPIUtil util = new BonitaAPIUtil();
        // the upload answers with JSON as text/plain: the filter is needed for this target only
        util.setWebTarget(client.target(bonitaAPIConfig.getBaseBonitaURI()).register(new JsonClientFilter()));
        return util;
    }

    public UploadFileResponse uploadFile(
            ProcessDefinitionResponse processDefinition,
            FileInput file)
            throws Exception {
        WebTarget resource = webTarget
                .path("portal/resource/process/{processName}/{processVersion}/API/formFileUpload")
                .resolveTemplate("processName", processDefinition.getName())
                .resolveTemplate("processVersion", processDefinition.getVersion());

        // the temporary file only reserves a unique name for the part: the content is sent from the byte array
        File tempFile = Files.createTempFile("tempFile", ".tmp").toFile();
        try {
            String dispositionValue = String.format("form-data;filename=%s;name=file", tempFile.getName());
            Attachment attachment = new AttachmentBuilder()
                    .object(new ByteArrayInputStream(file.getContent()))
                    .contentDisposition(new ContentDisposition(dispositionValue))
                    .build();

            return resource.request().accept(MediaType.APPLICATION_JSON).post(
                    entity(attachment, MediaType.MULTIPART_FORM_DATA), UploadFileResponse.class);
        } finally {
            // File.delete does not throw, so a failure of the upload is never replaced by one of the clean-up
            tempFile.delete();
        }
    }

    public Map<String, Serializable> prepareInputs(
            ProcessDefinitionResponse processDefinition,
            Map<String, Serializable> inputs)
            throws Exception {
        for (Entry<String, Serializable> entry : inputs.entrySet()) {
            if (entry.getValue() instanceof FileInput) {
                FileInput file = (FileInput) entry.getValue();
                String tmpFile = uploadFile(processDefinition, file).getTempPath();
                HashMap<String, Serializable> fileInput = new HashMap<>();
                fileInput.put("filename", file.getFilename());
                fileInput.put("tempPath", tmpFile);
                entry.setValue(fileInput);
            }
        }
        return inputs;
    }

    public WebTarget getWebTarget() {
        return webTarget;
    }

    public void setWebTarget(WebTarget webTarget) {
        this.webTarget = webTarget;
    }

}
