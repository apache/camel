package org.apache.camel.component.azure.storage.blob.operations;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Properties;

import com.azure.storage.blob.specialized.BlobInputStream;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.BlobTestUtils;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlobOperationsIT extends CamelTestSupport {

    private BlobConfiguration configuration;
    private BlobContainerClientWrapper blobContainerClientWrapper;

    @BeforeAll
    public void setup() throws IOException {
        final Properties properties = BlobTestUtils.loadAzurePropertiesFile();

        configuration = new BlobConfiguration();
        configuration.setAccountName(properties.getProperty("account_name"));
        configuration.setAccessKey(properties.getProperty("access_key"));
        configuration.setContainerName("test");

        blobContainerClientWrapper = new BlobServiceClientWrapper(BlobClientFactory.createBlobServiceClient(configuration))
                .getBlobContainerClientWrapper(configuration.getContainerName());
    }

    @Test
    public void testGetBlob(@TempDir Path testDir) throws IOException {
        // first: test with no exchange provided
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("test_file");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        final BlobOperationResponse response = operations.getBlob(null);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders());
        assertNotNull(response.getHeaders().get(BlobConstants.CREATION_TIME));

        // second: test with exchange set but no outputstream set
        final Exchange exchange = new DefaultExchange(context);
        final BlobOperationResponse response1 = operations.getBlob(exchange);

        assertNotNull(response1);
        assertNotNull(response1.getBody());
        assertNotNull(response1.getHeaders());

        final BlobInputStream inputStream = (BlobInputStream) response1.getBody();
        final String bufferedText = new BufferedReader(new InputStreamReader(inputStream)).readLine();

        assertEquals("awesome camel!", bufferedText);

        // third: test with outputstream set on exchange
        final File fileToWrite = new File(testDir.toFile(), "test_file.txt");
        exchange.getIn().setBody(new FileOutputStream(fileToWrite));

        final BlobOperationResponse response2 = operations.getBlob(exchange);
        final String fileContent = FileUtils.readFileToString(fileToWrite, Charset.defaultCharset());

        assertNotNull(response2);
        assertNotNull(response2.getBody());
        assertNotNull(response2.getHeaders());
        assertTrue(fileContent.contains("awesome camel!"));
    }
}