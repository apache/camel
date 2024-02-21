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
package org.apache.camel.component.platform.http.vertx;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "performance-tests", matches = ".*")
public class VertxPlatformHttpLargeMessageStreamingTest {

    @Test
    void testStreamingWithLargeRequestAndResponseBody() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        context.getStreamCachingStrategy().setSpoolEnabled(true);

        Path input = createLargeFile();
        Path output = Files.createTempFile("platform-http-output", "dat");

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/streaming?useStreaming=true")
                            .log("Done echoing back request body as response body");
                }
            });

            context.start();

            InputStream response = given()
                    .body(new FileInputStream(input.toFile()))
                    .post("/streaming")
                    .then()
                    .extract()
                    .asInputStream();

            try (FileOutputStream fos = new FileOutputStream(output.toFile())) {
                IOHelper.copy(response, fos);
            }

            assertEquals(input.toFile().length(), output.toFile().length());
        } finally {
            context.stop();
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }

    private Path createLargeFile() throws IOException {
        // Create a 4GB file containing random data
        Path path = Files.createTempFile("platform-http-input", "dat");
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            Random random = new Random();
            long targetFileSize = (long) (4 * Math.pow(1024, 3));
            long bytesWritten = 0L;

            byte[] data = new byte[1024];
            while (bytesWritten < targetFileSize) {
                random.nextBytes(data);
                fos.write(data);
                bytesWritten += data.length;
            }
        }
        return path;
    }
}
