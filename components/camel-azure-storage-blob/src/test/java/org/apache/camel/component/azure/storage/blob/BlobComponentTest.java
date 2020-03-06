package org.apache.camel.component.azure.storage.blob;

import java.util.List;
import java.util.Locale;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobContainerItem;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.common.StorageSharedKeyCredential;
import jdk.nashorn.internal.ir.annotations.Ignore;
import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlobComponentTest extends CamelTestSupport {

    @EndpointInject("direct:listBuckets")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Disabled
    @Test
    public void playWithClient() {
        String accountName = "cameldev";
        String accountKey = "----";

        /*
         * Use your Storage account's name and key to create a credential object; this is used to access your account.
         */
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountName, accountKey);

        /*
         * From the Azure portal, get your Storage account blob service URL endpoint.
         * The URL typically looks like this:
         */
        String endpoint = String.format(Locale.ROOT, "https://%s.blob.core.windows.net", accountName);

        /*
         * Create a BlobServiceClient object that wraps the service endpoint, credential and a request pipeline.
         */
        BlobServiceClient storageClient = new BlobServiceClientBuilder().endpoint(endpoint).credential(credential).buildClient();

        BlobContainerClient containerClient = storageClient.getBlobContainerClient("test");


        /*
         * List the containers' name under the Azure storage account.
         */
        storageClient.listBlobContainers().forEach(containerItem -> {
            System.out.println("Container name: " + containerItem.getName());
        });

        containerClient.listBlobs().forEach(blobItem -> {
            System.out.println("Blob name:" + blobItem.getName());
        });
    }

    //@Disabled
    @Test
    public void sendIn() throws Exception {
        template.sendBody("direct:listBuckets", ExchangePattern.InOnly, "");

        //result.expectedMessageCount(10);

        final List<BlobItem> containerItems = result.getExchanges().get(0).getIn().getBody(List.class);

        //result.assertIsSatisfied(1000000);

        containerItems.forEach(blobContainerItem -> {
            System.out.println(blobContainerItem.getName());
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final String uri = "azure-storage-blob://cameldev/test?accessKey=RAW(3T7sqN/v3vzyzYfNnAGpu/j3zExYDAJaxzRHWKnyH4cllcCMSUUuU/YYVp/X8qIin2++UQ6NGEEY0HgipEx8Ig==)";

                from("direct:listBuckets").to(uri + "&operation=listBlobs").to("mock:result");
            }
        };
    }

}