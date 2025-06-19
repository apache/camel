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
package org.apache.camel.maven.example;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.loader.tools.Libraries;
import org.springframework.boot.loader.tools.Library;
import org.springframework.boot.loader.tools.LibraryCallback;
import org.springframework.boot.loader.tools.LibraryScope;
import org.springframework.boot.loader.tools.Repackager;

/**
 * Example demonstrating how to use Spring Boot's Repackager to create self-executing JARs.
 * This shows the core concept that the Maven plugin implements.
 */
public class RepackagerExample {

    public static void main(String[] args) throws IOException {
        // This is a conceptual example showing how the Spring Boot Repackager works
        
        // 1. Start with a regular JAR containing your application classes
        File sourceJar = new File("target/my-app-1.0.jar");
        
        // 2. Create a repackager instance
        Repackager repackager = new Repackager(sourceJar);
        
        // 3. Configure the repackager
        repackager.setMainClass("org.apache.camel.dsl.jbang.launcher.CamelLauncher");
        repackager.setBackupSource(false);
        
        // 4. Define the output file
        File outputJar = new File("target/my-app-1.0-executable.jar");
        
        // 5. Repackage with dependencies
        repackager.repackage(outputJar, new ExampleLibraries());
        
        System.out.println("Created self-executing JAR: " + outputJar);
        System.out.println("Run with: java -jar " + outputJar);
        System.out.println("Or directly: ./" + outputJar.getName() + " (if executable=true)");
    }
    
    /**
     * Example implementation of Libraries interface that provides dependencies.
     * In the real Maven plugin, this would iterate over Maven artifacts.
     */
    static class ExampleLibraries implements Libraries {

        @Override
        public void doWithLibraries(LibraryCallback callback) throws IOException {
            // Example dependencies that would be included
            List<String> exampleDeps = Arrays.asList(
                "camel-jbang-core-4.12.0.jar",
                "camel-main-4.12.0.jar",
                "jackson-core-2.15.2.jar",
                "slf4j-api-1.7.36.jar"
            );

            for (String dep : exampleDeps) {
                File depFile = new File("dependencies/" + dep);
                if (depFile.exists()) {
                    System.out.println("Including library: " + dep);
                    callback.library(new Library(depFile, LibraryScope.COMPILE));
                } else {
                    System.out.println("Would include library: " + dep + " (file not found for demo)");
                }
            }
        }
    }
    
    /**
     * This demonstrates what the repackaged JAR structure would look like:
     * 
     * my-app-1.0-executable.jar
     * |
     * +-META-INF/
     * |  +-MANIFEST.MF
     * |    Main-Class: org.springframework.boot.loader.launch.JarLauncher
     * |    Start-Class: org.apache.camel.dsl.jbang.launcher.CamelLauncher
     * |
     * +-org/springframework/boot/loader/
     * |  +-launch/JarLauncher.class
     * |  +-jar/NestedJarFile.class
     * |  +-... (other Spring Boot loader classes)
     * |
     * +-BOOT-INF/
     *    +-classes/
     *    |  +-org/apache/camel/dsl/jbang/launcher/CamelLauncher.class
     *    |  +-... (your application classes)
     *    |
     *    +-lib/
     *       +-camel-jbang-core-4.12.0.jar
     *       +-camel-main-4.12.0.jar
     *       +-jackson-core-2.15.2.jar
     *       +-slf4j-api-1.7.36.jar
     *       +-... (all other dependency JARs)
     * 
     * When you run: java -jar my-app-1.0-executable.jar
     * OR directly: ./my-app-1.0-executable.jar (if executable=true)
     *
     * 1. If executable: Shell script finds Java and executes the JAR portion
     * 2. JVM starts JarLauncher (Spring Boot's launcher)
     * 3. JarLauncher sets up a custom ClassLoader that can read from nested JARs
     * 4. JarLauncher loads your main class (CamelLauncher) from BOOT-INF/classes/
     * 5. Your application runs, loading dependencies from BOOT-INF/lib/ as needed
     *
     * The executable JAR structure with launcher script:
     * #!/bin/bash
     * # Launcher script that finds Java and executes this file as a JAR
     * exec java -jar "$0" "$@"
     * # === JAR CONTENT STARTS BELOW ===
     * PK... (actual JAR bytes)
     */
}
