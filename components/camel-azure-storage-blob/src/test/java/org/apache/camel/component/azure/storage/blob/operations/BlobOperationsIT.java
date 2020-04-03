package org.apache.camel.component.azure.storage.blob.operations;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import com.azure.core.exception.UnexpectedLengthException;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.AppendBlobRequestConditions;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.azure.storage.blob.specialized.BlockBlobClient;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobBlock;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.BlobExchangeHeaders;
import org.apache.camel.component.azure.storage.blob.BlobTestUtils;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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

    @Test
    public void testUploadBlockBlob() throws Exception {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("upload_test_file");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        // first: test as file provided
        final File fileToUpload = new File(Objects.requireNonNull(getClass().getClassLoader().getResource("upload_test_file")).getFile());
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(fileToUpload);

        final BlobOperationResponse response = operations.uploadBlockBlob(exchange);

        assertNotNull(response);
        assertTrue((boolean)response.getBody());
        // check for eTag and md5 to make sure is uploaded
        assertNotNull(response.getHeaders().get(BlobConstants.E_TAG));
        assertNotNull(response.getHeaders().get(BlobConstants.CONTENT_MD5));

        // check content
        final BlobOperationResponse getBlobResponse = operations.getBlob(null);

        assertEquals("awesome camel to upload!", IOUtils.toString((InputStream) getBlobResponse.getBody(), Charset.defaultCharset()));

        blobClientWrapper.delete(null, null, null);

        // second: test as string provided
        final String data = "Hello world from my awesome tests!";
        final InputStream dataStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        exchange.getIn().setBody(dataStream);

        final BlobOperationResponse response2 = operations.uploadBlockBlob(exchange);

        assertNotNull(response2);
        assertTrue((boolean)response2.getBody());
        // check for eTag and md5 to make sure is uploaded
        assertNotNull(response2.getHeaders().get(BlobConstants.E_TAG));
        assertNotNull(response2.getHeaders().get(BlobConstants.CONTENT_MD5));

        // check content
        final BlobOperationResponse getBlobResponse2 = operations.getBlob(null);

        assertEquals(data, IOUtils.toString((InputStream) getBlobResponse2.getBody(), Charset.defaultCharset()));

        blobClientWrapper.delete(null, null, null);
    }

    @Test
    public void testCommitAndStageBlockBlob() throws Exception {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("upload_test_file");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        final List<BlobBlock> blocks = new LinkedList<>();
        blocks.add(BlobBlock.createBlobBlock(new ByteArrayInputStream("Hello".getBytes())));
        blocks.add(BlobBlock.createBlobBlock(new ByteArrayInputStream("From".getBytes())));
        blocks.add(BlobBlock.createBlobBlock(new ByteArrayInputStream("Camel".getBytes())));

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(blocks);

        final BlobOperationResponse response = operations.stageBlockBlobList(exchange);

        assertNotNull(response);
        assertNotNull(response.getBody());
        // check for eTag and md5 to make sure is uploaded
        assertNotNull(response.getHeaders().get(BlobConstants.E_TAG));

        final BlobOperationResponse getBlobResponse = operations.getBlob(null);

        assertEquals("HelloFromCamel", IOUtils.toString((InputStream) getBlobResponse.getBody(), Charset.defaultCharset()));

        blobClientWrapper.delete(null, null, null);
    }

    @Test
    public void testGetBlobBlockList() {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("test_file");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        final BlobOperationResponse response = operations.getBlobBlockList(null);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders());
    }

    @Test
    public void testCreateAndUpdateAppendBlob() throws IOException {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("upload_test_file");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        final String data = "Hello world from my awesome tests!";
        final InputStream dataStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(dataStream);

        final BlobOperationResponse response = operations.updateAppendBlob(exchange);

        assertNotNull(response);
        assertTrue((boolean)response.getBody());
        // check for eTag and md5 to make sure is uploaded
        assertNotNull(response.getHeaders().get(BlobConstants.E_TAG));
        assertNotNull(response.getHeaders().get(BlobConstants.COMMITTED_BLOCK_COUNT));

        // check content
        final BlobOperationResponse getBlobResponse = operations.getBlob(null);

        assertEquals(data, IOUtils.toString((InputStream) getBlobResponse.getBody(), Charset.defaultCharset()));

        blobClientWrapper.delete(null, null, null);
    }
}