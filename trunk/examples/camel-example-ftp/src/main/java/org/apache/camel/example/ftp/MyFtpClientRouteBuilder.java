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
package org.apache.camel.example.ftp;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;

/**
 * Client route
 */
public class MyFtpClientRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // configure properties component
        PropertiesComponent pc = getContext().getComponent("properties", PropertiesComponent.class);
        pc.setLocation("classpath:ftp.properties");

        // lets shutdown faster in case of in-flight messages stack up
        getContext().getShutdownStrategy().setTimeout(10);

        from("file:target/upload?moveFailed=../error")
            .log("Uploading file ${file:name}")
            .to("{{ftp.client}}")
            .log("Uploaded file ${file:name} complete.");

        // use system out so it stand out
        System.out.println("*********************************************************************************");
        System.out.println("Camel will route files from target/upload directory to the FTP server: "
                + getContext().resolvePropertyPlaceholders("{{ftp.server}}"));
        System.out.println("You can configure the location of the ftp server in the src/main/resources/ftp.properties file.");
        System.out.println("If the file upload fails, then the file is moved to the target/error directory.");
        System.out.println("Use ctrl + c to stop this application.");
        System.out.println("*********************************************************************************");
    }
}
