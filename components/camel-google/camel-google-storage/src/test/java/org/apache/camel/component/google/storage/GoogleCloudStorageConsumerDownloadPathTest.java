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
package org.apache.camel.component.google.storage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.google.storage.localstorage.LocalStorageHelper;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Verifies that the local download path the consumer builds from a remote object name stays within the configured
 * {@code downloadFileName} directory.
 */
class GoogleCloudStorageConsumerDownloadPathTest extends CamelTestSupport {

    private static final String DOWNLOAD_DIR = "target/gcs-download-path";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        GoogleCloudStorageComponent component = context.getComponent("google-storage", GoogleCloudStorageComponent.class);
        component.getConfiguration().setStorageClient(LocalStorageHelper.getOptions().getService());
        return context;
    }

    private GoogleCloudStorageConsumer createConsumer(String downloadFileName) throws Exception {
        GoogleCloudStorageEndpoint endpoint = context.getEndpoint(
                "google-storage://myCamelBucket?autoCreateBucket=true&downloadFileName=" + downloadFileName,
                GoogleCloudStorageEndpoint.class);
        return (GoogleCloudStorageConsumer) endpoint.createConsumer(exchange -> {
        });
    }

    @Test
    void plainObjectNameResolvesInsideDownloadDirectory() throws Exception {
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThat(consumer.evaluateFileExpression(exchange, DOWNLOAD_DIR, "file.txt"))
                .isEqualTo(DOWNLOAD_DIR + "/file.txt");
    }

    @Test
    void nestedObjectNameResolvesInsideDownloadDirectory() throws Exception {
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThat(consumer.evaluateFileExpression(exchange, DOWNLOAD_DIR, "nested/file.txt"))
                .isEqualTo(DOWNLOAD_DIR + "/nested/file.txt");
    }

    @Test
    void objectNameWithParentSegmentIsRejected() throws Exception {
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.evaluateFileExpression(exchange, DOWNLOAD_DIR, "../escape.txt"))
                .withMessageContaining("../escape.txt");
    }

    @Test
    void objectNameWithParentSegmentNestedInTheKeyIsRejected() throws Exception {
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.evaluateFileExpression(exchange, DOWNLOAD_DIR, "nested/../../escape.txt"));
    }

    @Test
    void plainObjectNameResolvesInsideStaticPrefixOfExpression() throws Exception {
        // the ${file:name} token resolves to the remote object name, so the static directory prefix of the configured
        // downloadFileName confines the download just like a plain directory does
        String expression = DOWNLOAD_DIR + "/${file:name}";
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThat(consumer.evaluateFileExpression(exchange, expression, "file.txt"))
                .isEqualTo(DOWNLOAD_DIR + "/file.txt");
    }

    @Test
    void nestedObjectNameResolvesInsideStaticPrefixOfExpression() throws Exception {
        String expression = DOWNLOAD_DIR + "/${file:name}";
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThat(consumer.evaluateFileExpression(exchange, expression, "nested/file.txt"))
                .isEqualTo(DOWNLOAD_DIR + "/nested/file.txt");
    }

    @Test
    void objectNameWithParentSegmentIsRejectedOnTheExpressionBranch() throws Exception {
        String expression = DOWNLOAD_DIR + "/${file:name}";
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.evaluateFileExpression(exchange, expression, "../escape.txt"))
                .withMessageContaining("../escape.txt");
    }

    @Test
    void objectNameJoiningTheExpressionOutOfTheStaticPrefixIsRejected() throws Exception {
        // ./escape.txt has no .. segment of its own, but /.${file:name} turns it into a parent segment, so the static
        // directory prefix still has to confine the result
        String expression = DOWNLOAD_DIR + "/.${file:name}";
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.evaluateFileExpression(exchange, expression, "./escape.txt"))
                .withMessageContaining("./escape.txt")
                .withMessageContaining(DOWNLOAD_DIR);
    }

    @Test
    void objectNameWithParentSegmentNestedInTheKeyIsRejectedOnTheExpressionBranch() throws Exception {
        String expression = DOWNLOAD_DIR + "/${file:name}";
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.evaluateFileExpression(exchange, expression, "nested/../../escape.txt"));
    }

    @Test
    void objectNameNormalizingBackInsideIsRejected() throws Exception {
        // a .. segment is refused outright, even when it would normalize back inside the download directory
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.evaluateFileExpression(exchange, DOWNLOAD_DIR, "nested/../file.txt"))
                .withMessageContaining("nested/../file.txt");
    }

    @Test
    void plainObjectNameOnAFullyDynamicExpressionIsAccepted() throws Exception {
        String expression = "${file:name}";
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThat(consumer.evaluateFileExpression(exchange, expression, "nested/file.txt"))
                .isEqualTo("nested/file.txt");
    }

    @Test
    void objectNameWithParentSegmentIsRejectedOnAFullyDynamicExpression() throws Exception {
        // the route author configured no directory, but the object name is still untrusted input
        String expression = "${file:name}";
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.evaluateFileExpression(exchange, expression,
                        "../../home/app/.ssh/authorized_keys"))
                .withMessageContaining("../../home/app/.ssh/authorized_keys");
    }

    @Test
    void absoluteObjectNameIsRejectedOnAFullyDynamicExpression() throws Exception {
        String expression = "${file:name}";
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.evaluateFileExpression(exchange, expression, "/etc/cron.d/escape"))
                .withMessageContaining("/etc/cron.d/escape");
    }

    @Test
    void absoluteObjectNameAfterAFileNamePrefixIsRejected() throws Exception {
        // prefix-/../../escape.txt would normalize to ../escape.txt
        String expression = "prefix-${file:name}";
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.evaluateFileExpression(exchange, expression, "/../../escape.txt"));
    }

    @Test
    void objectNameJoiningTheExpressionIntoAParentSegmentIsRejected() throws Exception {
        // ./file.txt has no .. segment of its own, but .${file:name} turns it into ../file.txt
        String expression = ".${file:name}";
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.evaluateFileExpression(exchange, expression, "./file.txt"))
                .withMessageContaining("working directory");
    }

    @Test
    void objectWithARejectedNameIsSkippedWithoutStoppingTheOthers() throws Exception {
        // the rejected object is reported and skipped, while the other objects of the same poll are still consumed
        // (the accepted objects are really downloaded, and the consumer does not create the download directory)
        Files.createDirectories(Path.of(DOWNLOAD_DIR));
        GoogleCloudStorageEndpoint endpoint = context.getEndpoint(
                "google-storage://myRejectBucket?autoCreateBucket=true", GoogleCloudStorageEndpoint.class);
        endpoint.getConfiguration().setDownloadFileName(DOWNLOAD_DIR + "/${exchangeId}.bin");
        GoogleCloudStorageConsumer consumer = (GoogleCloudStorageConsumer) endpoint.createConsumer(exchange -> {
        });
        endpoint.start();
        consumer.init();
        List<String> reported = new ArrayList<>();
        consumer.setExceptionHandler(new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                reported.add(exception.getMessage());
            }

            @Override
            public void handleException(String message, Throwable exception) {
                reported.add(message);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                reported.add(message);
            }
        });
        Storage storage = endpoint.getStorageClient();
        List<Blob> blobs = new ArrayList<>();
        for (String name : List.of("a.txt", "b/../c.txt", "d.txt")) {
            blobs.add(storage.create(BlobInfo.newBuilder("myRejectBucket", name).build(),
                    name.getBytes(StandardCharsets.UTF_8)));
        }

        Queue<Exchange> exchanges = consumer.createExchanges(blobs);

        assertThat(exchanges)
                .extracting(e -> e.getMessage().getHeader(GoogleCloudStorageConstants.OBJECT_NAME, String.class))
                .containsExactly("a.txt", "d.txt");
        assertThat(reported).hasSize(1);
        assertThat(reported.get(0)).contains("b/../c.txt");
    }

    @Test
    void fullyDynamicExpressionResolvingToAnAbsoluteDirectoryKeepsWorking() throws Exception {
        // the directory comes from the route author's own expression, so an absolute result is not confined
        String directory = new File(DOWNLOAD_DIR).getAbsolutePath();
        String expression = "${header.dir}/${file:name}";
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("dir", directory);

        assertThat(consumer.evaluateFileExpression(exchange, expression, "nested/file.txt"))
                .isEqualTo(directory + "/nested/file.txt");
    }
}
