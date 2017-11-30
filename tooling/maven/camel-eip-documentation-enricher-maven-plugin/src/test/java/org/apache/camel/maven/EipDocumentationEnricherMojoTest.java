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
package org.apache.camel.maven;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EipDocumentationEnricherMojoTest {

    private EipDocumentationEnricherMojo eipDocumentationEnricherMojo = new EipDocumentationEnricherMojo();

    @Mock
    private File mockCamelCore;

    @Mock
    private File mockInputSchema;


    @Before
    public void setUp() throws Exception {
        eipDocumentationEnricherMojo.camelCoreDir = mockCamelCore;
        eipDocumentationEnricherMojo.inputCamelSchemaFile = mockInputSchema;
        eipDocumentationEnricherMojo.pathToModelDir = "sub/path";
    }

    @Test
    public void testExecuteCamelCoreDoesNotExist() throws Exception {
        when(mockCamelCore.exists()).thenReturn(false);
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
    public void testExecuteCamelCoreIsNull() throws Exception {
        eipDocumentationEnricherMojo.camelCoreDir = null;

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
    public void testExecuteCamelCoreIsNotADirectory() throws Exception {
        when(mockCamelCore.exists()).thenReturn(true);
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
    public void testExecuteInputCamelSchemaDoesNotExist() throws Exception {
        when(mockInputSchema.exists()).thenReturn(false);

        try {
            eipDocumentationEnricherMojo.execute();
            fail("Expected MojoExecutionException");
        } catch (MojoExecutionException e) {
            // Expected.
        }
    }

    @Test
    public void testExecuteInputCamelSchemaIsNull() throws Exception {
        eipDocumentationEnricherMojo.inputCamelSchemaFile = null;

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