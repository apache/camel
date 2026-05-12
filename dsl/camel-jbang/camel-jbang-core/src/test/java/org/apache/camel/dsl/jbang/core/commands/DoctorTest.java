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
package org.apache.camel.dsl.jbang.core.commands;

import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoctorTest {

    @Test
    void shouldReturnZeroExitCode() throws Exception {
        StringPrinter printer = new StringPrinter();
        Doctor doctor = new Doctor(new CamelJBangMain().withPrinter(printer));

        int exit = doctor.doCall();

        assertEquals(0, exit);
    }

    @Test
    void shouldPrintHeader() throws Exception {
        StringPrinter printer = new StringPrinter();
        Doctor doctor = new Doctor(new CamelJBangMain().withPrinter(printer));

        doctor.doCall();

        String output = printer.getOutput();
        assertTrue(output.contains("Camel JBang Doctor"), "Should contain header");
        assertTrue(output.contains("=================="), "Should contain separator");
    }

    @Test
    void shouldCheckJavaVersion() throws Exception {
        StringPrinter printer = new StringPrinter();
        Doctor doctor = new Doctor(new CamelJBangMain().withPrinter(printer));

        doctor.doCall();

        String output = printer.getOutput();
        assertTrue(output.contains("Java:"), "Should report Java version");
        String javaVersion = System.getProperty("java.version");
        assertTrue(output.contains(javaVersion), "Should contain actual Java version");
    }

    @Test
    void shouldCheckCamelVersion() throws Exception {
        StringPrinter printer = new StringPrinter();
        Doctor doctor = new Doctor(new CamelJBangMain().withPrinter(printer));

        doctor.doCall();

        String output = printer.getOutput();
        assertTrue(output.contains("Camel:"), "Should report Camel version");
    }

    @Test
    void shouldCheckPorts() throws Exception {
        StringPrinter printer = new StringPrinter();
        Doctor doctor = new Doctor(new CamelJBangMain().withPrinter(printer));

        doctor.doCall();

        String output = printer.getOutput();
        assertTrue(output.contains("Ports:"), "Should report port status");
    }

    @Test
    void shouldCheckDiskSpace() throws Exception {
        StringPrinter printer = new StringPrinter();
        Doctor doctor = new Doctor(new CamelJBangMain().withPrinter(printer));

        doctor.doCall();

        String output = printer.getOutput();
        assertTrue(output.contains("Disk space:"), "Should report disk space");
        assertTrue(output.contains("MB free"), "Should report free MB");
    }

    @Test
    void shouldCheckAllSections() throws Exception {
        StringPrinter printer = new StringPrinter();
        Doctor doctor = new Doctor(new CamelJBangMain().withPrinter(printer));

        doctor.doCall();

        String output = printer.getOutput();
        assertTrue(output.contains("Java:"), "Should check Java");
        assertTrue(output.contains("JBang:"), "Should check JBang");
        assertTrue(output.contains("Camel:"), "Should check Camel");
        assertTrue(output.contains("Maven:"), "Should check Maven");
        assertTrue(output.contains("Container:"), "Should check Container");
        assertTrue(output.contains("Ports:"), "Should check Ports");
        assertTrue(output.contains("Disk space:"), "Should check Disk space");
    }
}
