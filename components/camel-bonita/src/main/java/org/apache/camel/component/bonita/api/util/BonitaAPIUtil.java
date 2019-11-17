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
import java.io.FileOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.apache.camel.component.bonita.api.filter.BonitaAuthFilter;
import org.apache.camel.component.bonita.api.filter.JsonClientFilter;
import org.apache.camel.component.bonita.api.model.FileInput;
import org.apache.camel.component.bonita.api.model.ProcessDefinitionResponse;
import org.apache.camel.component.bonita.api.model.UploadFileResponse;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.AttachmentBuilder;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;

import static javax.ws.rs.client.Entity.entity;

public class BonitaAPIUtil {

    private static BonitaAPIUtil instance;
    private WebTarget webTarget;

    public static BonitaAPIUtil getInstance(BonitaAPIConfig bonitaAPIConfig) {
        if (instance == null) {
            instance = new BonitaAPIUtil();
            ClientBuilder clientBuilder = ClientBuilder.newBuilder();
            Client client = clientBuilder.build();
            client.register(JacksonJsonProvider.class);
            client.register(new JsonClientFilter());
            client.register(new BonitaAuthFilter(bonitaAPIConfig));
            instance.setWebTarget(client.target(bonitaAPIConfig.getBaseBonitaURI()));
        }
        return instance;
    }

    public UploadFileResponse uploadFile(ProcessDefinitionResponse processDefinition,
            FileInput file) throws Exception {
        WebTarget resource = webTarget
            .path("portal/resource/process/{processName}/{processVersion}/API/formFileUpload")
            .resolveTemplate("processName", processDefinition.getName())
            .resolveTemplate("processVersion", processDefinition.getVersion());

        File tempFile = Files.createTempFile("tempFile", ".tmp").toFile();
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write(file.getContent());
        fos.close();

        String dispositionValue = String.format("form-data;filename=%s;name=file", tempFile.getName());
        Attachment attachment = new AttachmentBuilder()
                .object(new ByteArrayInputStream(file.getContent()))
                .contentDisposition(new ContentDisposition(dispositionValue))
                .build();

        return resource.request().accept(MediaType.APPLICATION_JSON).post(
                entity(attachment, MediaType.MULTIPART_FORM_DATA), UploadFileResponse.class);
    }

    public Map<String, Serializable> prepareInputs(ProcessDefinitionResponse processDefinition,
            Map<String, Serializable> inputs) throws Exception {
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
