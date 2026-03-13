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
package org.apache.camel.component.springai.image;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the Spring AI Image component using Ollama's OpenAI-compatible API.
 *
 * Uses x/flux2-klein:4b model running on Ollama, accessed via OpenAI base URL override. The model must be pre-pulled in
 * Ollama.
 *
 * Ollama returns application/x-ndjson content type, so a custom RestClient.Builder is used to register a Jackson
 * converter that supports this media type.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiImageOllamaIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SpringAiImageOllamaIT.class);

    private static final String IMAGE_MODEL_NAME = "x/flux2-klein:4b";

    @TempDir(
    /* cleanup = CleanupMode.NEVER */ // Uncomment to double check the image
    )
    Path tempDir;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    private ImageModel imageModel;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        LOG.info("*******************************************************************************");
        LOG.info("* Generated images directory: {}", tempDir.toAbsolutePath());
        LOG.info("*******************************************************************************");
        LOG.info("Setting up ImageModel with Ollama at {} using model '{}'", OLLAMA.baseUrl(), IMAGE_MODEL_NAME);

        // Ollama returns application/x-ndjson which Spring's default RestClient doesn't handle.
        // Add a Jackson converter that supports this media type.
        MappingJackson2HttpMessageConverter ndjsonConverter = new MappingJackson2HttpMessageConverter();
        ndjsonConverter.setSupportedMediaTypes(
                java.util.List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("application/x-ndjson")));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .messageConverters(converters -> converters.add(0, ndjsonConverter));

        OpenAiImageApi imageApi = OpenAiImageApi.builder()
                .baseUrl(OLLAMA.baseUrl())
                .apiKey("unused")
                .restClientBuilder(restClientBuilder)
                .build();

        // Only set the model name here; width/height are configured on the Camel endpoint or via headers
        OpenAiImageOptions defaultOptions = OpenAiImageOptions.builder()
                .model(IMAGE_MODEL_NAME)
                .build();

        imageModel = new OpenAiImageModel(imageApi, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
        LOG.info("ImageModel initialized successfully");
    }

    @Test
    public void testImageGeneration() {
        String prompt = "A camel walking through a beautiful sunset over the ocean";
        LOG.info("testImageGeneration - Sending prompt: '{}'", prompt);

        Exchange exchange = template().request("direct:generate", e -> {
            e.getIn().setBody(prompt);
        });

        assertThat(exchange).isNotNull();
        assertThat(exchange.getException()).isNull();

        Object body = exchange.getMessage().getBody();
        assertThat(body).isInstanceOf(Image.class);

        Image image = (Image) body;
        logImageResult("testImageGeneration", image);
        assertThat(image.getUrl() != null || image.getB64Json() != null)
                .as("Image should have URL or base64 data")
                .isTrue();
    }

    @Test
    public void testImageGenerationWithOptions() {
        String prompt = "A camel standing next to a red apple on a white table";
        LOG.info("testImageGenerationWithOptions - Sending prompt: '{}' with model={}", prompt, IMAGE_MODEL_NAME);

        Exchange exchange = template().request("direct:generateWithOptions", e -> {
            e.getIn().setBody(prompt);
        });

        assertThat(exchange).isNotNull();
        assertThat(exchange.getException()).isNull();

        Object body = exchange.getMessage().getBody();
        assertThat(body).isInstanceOf(Image.class);
        logImageResult("testImageGenerationWithOptions", (Image) body);
    }

    @Test
    public void testImageGenerationWithHeaderOverrides() {
        String prompt = "A camel riding through the desert under a blue sky with white clouds";
        LOG.info("testImageGenerationWithHeaderOverrides - Sending prompt: '{}' with headers: model={}, width=256, height=256",
                prompt, IMAGE_MODEL_NAME);

        Exchange exchange = template().request("direct:generate", e -> {
            e.getIn().setBody(prompt);
            e.getIn().setHeader(SpringAiImageHeaders.MODEL, IMAGE_MODEL_NAME);
            // Override width/height via headers (smaller than endpoint's 512x512)
            e.getIn().setHeader(SpringAiImageHeaders.WIDTH, 256);
            e.getIn().setHeader(SpringAiImageHeaders.HEIGHT, 256);
        });

        assertThat(exchange).isNotNull();
        assertThat(exchange.getException()).isNull();

        Object body = exchange.getMessage().getBody();
        assertThat(body).isInstanceOf(Image.class);
        logImageResult("testImageGenerationWithHeaderOverrides", (Image) body);
    }

    @Test
    public void testImageGenerationSaveToFile() throws Exception {
        String prompt = "A camel resting under a green tree in a field";
        LOG.info("testImageGenerationSaveToFile - Sending prompt: '{}', saving via file component to {}",
                prompt, tempDir.toAbsolutePath());

        Exchange exchange = template().request("direct:generateAndSave", e -> {
            e.getIn().setBody(prompt);
        });

        assertThat(exchange).isNotNull();
        assertThat(exchange.getException()).isNull();

        // Verify the file was written by the Camel file component (via Image -> byte[] type converter)
        File outputFile = tempDir.resolve("camel-file-saved.png").toFile();
        assertThat(outputFile).exists();
        assertThat(outputFile.length()).isGreaterThan(0);
        LOG.info("testImageGenerationSaveToFile - Image saved via file component to {} ({} bytes)",
                outputFile.getAbsolutePath(), outputFile.length());
    }

    private void logImageResult(String testName, Image image) {
        if (image.getUrl() != null) {
            LOG.info("{} - Image URL: {}", testName, image.getUrl());
        }
        if (image.getB64Json() != null) {
            // Save the image to target/generated-images/ for inspection
            saveImage(testName, image);
        }
        if (image.getUrl() == null && image.getB64Json() == null) {
            LOG.warn("{} - Image has neither URL nor base64 data!", testName);
        }
    }

    private void saveImage(String testName, Image image) {
        if (image.getB64Json() == null) {
            return;
        }
        try {
            byte[] imageBytes = Base64.getDecoder().decode(image.getB64Json());
            Path outputPath = tempDir.resolve(testName + ".png");
            Files.write(outputPath, imageBytes);
            LOG.info("{} - Image saved to: {} ({} bytes)", testName, outputPath.toAbsolutePath(), imageBytes.length);
        } catch (Exception e) {
            LOG.warn("{} - Failed to save image: {}", testName, e.getMessage());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                getCamelContext().getRegistry().bind("imageModel", imageModel);

                // Width and height set on the Camel endpoint to reduce image size (~500KB)
                from("direct:generate")
                        .log("Generating image with prompt: ${body}")
                        .to("spring-ai-image:generate?imageModel=#imageModel&width=512&height=512")
                        .log("Image generated: ${body.getClass().getSimpleName()}");

                // Model, width, height and responseFormat all set via Camel endpoint options
                from("direct:generateWithOptions")
                        .log("Generating image with options, prompt: ${body}")
                        .to("spring-ai-image:generateWithOptions?imageModel=#imageModel"
                            + "&model=" + IMAGE_MODEL_NAME
                            + "&width=512&height=512")
                        .log("Image generated with options: ${body.getClass().getSimpleName()}");

                // Generate an image and save it to a file.
                // The Image -> byte[] conversion happens automatically via SpringAiImageConverter.
                from("direct:generateAndSave")
                        .log("Generating image for file save, prompt: ${body}")
                        .to("spring-ai-image:generateAndSave?imageModel=#imageModel&width=512&height=512")
                        .log("Image generated, saving to file (type conversion: Image -> byte[])")
                        .toF("file:%s?fileName=camel-file-saved.png", tempDir.toAbsolutePath())
                        .log("Image saved to file");
            }
        };
    }
}
