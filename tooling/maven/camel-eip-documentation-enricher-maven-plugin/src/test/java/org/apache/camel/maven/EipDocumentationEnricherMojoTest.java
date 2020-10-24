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
package org.apache.camel.maven;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EipDocumentationEnricherMojoTest {

    private EipDocumentationEnricherMojo eipDocumentationEnricherMojo = new EipDocumentationEnricherMojo();

    @Mock
    private File mockCamelCore;

    @Mock
    private File mockInputSchema;

    @BeforeEach
    public void setUp() throws Exception {
        eipDocumentationEnricherMojo.camelCoreModelDir = mockCamelCore;
        eipDocumentationEnricherMojo.inputCamelSchemaFile = mockInputSchema;
        eipDocumentationEnricherMojo.pathToModelDir = "sub/path";
    }

    @Test
    public void testExecuteCamelCoreIsNull() throws Exception {
        eipDocumentationEnricherMojo.camelCoreModelDir = null;

        when(mockInputSchema.exists()).thenReturn(true);
        when(mockInputSchema.isFile()).thenReturn(true);

        try {
            eipDocumentationEnricherMojo.execute();
            fail("Expected MojoExecutionException");
        } catch (MojoExecutionException e) {
            // Expected.
        }
    }

    @Test
    public void testExecuteInputCamelSchemaIsNotAFile() throws Exception {
        when(mockInputSchema.exists()).thenReturn(true);
        when(mockInputSchema.isFile()).thenReturn(false);

        try {
            eipDocumentationEnricherMojo.execute();
            fail("Expected MojoExecutionException");
        } catch (MojoExecutionException e) {
            // Expected.
        }
    }

    @Test
    public void testExecutePathToModelDirIsNull() throws Exception {
        eipDocumentationEnricherMojo.pathToModelDir = null;

        try {
            eipDocumentationEnricherMojo.execute();
            fail("Expected MojoExecutionException");
        } catch (MojoExecutionException e) {
            // Expected.
        }
    }
}
