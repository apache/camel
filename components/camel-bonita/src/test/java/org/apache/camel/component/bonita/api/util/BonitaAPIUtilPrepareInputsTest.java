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

package org.apache.camel.component.bonita.api.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.bonita.api.model.FileInput;
import org.apache.camel.component.bonita.api.model.ProcessDefinitionResponse;
import org.apache.camel.component.bonita.api.model.UploadFileResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
public class BonitaAPIUtilPrepareInputsTest {

    @Mock
    ProcessDefinitionResponse processDefinition;

    private BonitaAPIUtil bonitaApiUtil;

    @Before
    public void setup() {
        bonitaApiUtil = BonitaAPIUtil.getInstance(new BonitaAPIConfig("hostname", "port", "username", "password"));
    }

    @Test
    public void testPrepareInputsEmpty() throws Exception {
        Map<String, Serializable> rawInputs = new HashMap<>();
        Map<String, Serializable> inputs = bonitaApiUtil.prepareInputs(processDefinition, rawInputs);
        assertEquals(inputs.size(), rawInputs.size());
    }

    @Test
    public void testPrepareInputsNoFiles() throws Exception {
        Map<String, Serializable> rawInputs = new HashMap<>();
        rawInputs.put("myVariable", 1);
        Map<String, Serializable> inputs = bonitaApiUtil.prepareInputs(processDefinition, rawInputs);
        assertEquals(rawInputs.size(), inputs.size());
    }

    @Test
    public void testPrepareInputsOneFile() throws Exception {
        Map<String, Serializable> rawInputs = new HashMap<>();
        FileInput file = new FileInput("filename", "String".getBytes());
        rawInputs.put("myVariable", 1);
        rawInputs.put("filename", file);
        BonitaAPIUtil bonitaApiUtilMod = spy(bonitaApiUtil);
        UploadFileResponse uploadFileResponse = new UploadFileResponse();
        uploadFileResponse.setTempPath("temp");
        doReturn(uploadFileResponse).when(bonitaApiUtilMod).uploadFile(any(), any());
        Map<String, Serializable> inputs = bonitaApiUtilMod.prepareInputs(processDefinition, rawInputs);
        assertEquals(rawInputs.size(), inputs.size());
    }

    @Test
    public void testPrepareInputsFileType() throws Exception {
        Map<String, Serializable> rawInputs = new HashMap<>();
        FileInput file = new FileInput("filename", "String".getBytes());
        rawInputs.put("filename", file);
        BonitaAPIUtil bonitaApiUtilMod = spy(bonitaApiUtil);
        UploadFileResponse uploadFileResponse = new UploadFileResponse();
        uploadFileResponse.setTempPath("temp");
        doReturn(uploadFileResponse).when(bonitaApiUtilMod).uploadFile(any(), any());
        Map<String, Serializable> inputs = bonitaApiUtilMod.prepareInputs(processDefinition, rawInputs);
        assertTrue(Map.class.isInstance(inputs.get("filename")));
    }

    @Test
    public void testPrepareInputsTempFilePath() throws Exception {
        Map<String, Serializable> rawInputs = new HashMap<>();
        FileInput file = new FileInput("filename", "String".getBytes());
        rawInputs.put("filename", file);
        BonitaAPIUtil bonitaApiUtilMod = spy(bonitaApiUtil);
        UploadFileResponse uploadFileResponse = new UploadFileResponse();
        uploadFileResponse.setTempPath("temp");
        doReturn(uploadFileResponse).when(bonitaApiUtilMod).uploadFile(any(), any());
        Map<String, Serializable> inputs = bonitaApiUtilMod.prepareInputs(processDefinition, rawInputs);
        Map<String, Serializable> fileMap = (Map<String, Serializable>)inputs.get("filename");
        assertEquals("temp", fileMap.get("tempPath"));
    }
}
