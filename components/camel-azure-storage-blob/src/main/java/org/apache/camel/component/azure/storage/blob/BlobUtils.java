package org.apache.camel.component.azure.storage.blob;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.io.IOUtils;

public final class BlobUtils {

    public static Message getInMessage(final Exchange exchange) {
        return exchange.getIn();
    }

    public static Long getInputStreamLength(final InputStream inputStream) throws IOException {
        if (!inputStream.markSupported()) {
            throw new IllegalArgumentException("Reset inputStream is not supported, provide an inputstream with supported mark/reset.");
        }
        final long length = IOUtils.toByteArray(inputStream).length;
        inputStream.reset();

        return length;
    }

    public static BlobCommonRequestOptions getCommonRequestOptions(final Exchange exchange) {
        final BlobHttpHeaders blobHttpHeaders = BlobExchangeHeaders.getBlobHttpHeadersFromHeaders(exchange);
        final Map<String, String> metadata = BlobExchangeHeaders.getMetadataFromHeaders(exchange);
        final AccessTier accessTier = BlobExchangeHeaders.getAccessTierFromHeaders(exchange);
        final BlobRequestConditions blobRequestConditions = BlobExchangeHeaders.getBlobRequestConditionsFromHeaders(exchange);
        final Duration timeout = BlobExchangeHeaders.getTimeoutFromHeaders(exchange);
        final byte[] contentMD5 = BlobExchangeHeaders.getContentMd5FromHeaders(exchange);

        return new BlobCommonRequestOptions(blobHttpHeaders, metadata, accessTier, blobRequestConditions, contentMD5, timeout);
    }

    public static String getContainerName(final BlobConfiguration configuration, final Exchange exchange) {
        return ObjectHelper.isEmpty(BlobExchangeHeaders.getBlobContainerNameFromHeaders(exchange)) ? configuration.getContainerName() :
                BlobExchangeHeaders.getBlobContainerNameFromHeaders(exchange);
    }

    public static String getBlobName(final BlobConfiguration configuration, final Exchange exchange) {
        return ObjectHelper.isEmpty(BlobExchangeHeaders.getBlobNameFromHeaders(exchange)) ? configuration.getBlobName() :
                BlobExchangeHeaders.getBlobNameFromHeaders(exchange);
    }

    private BlobUtils() {
    }

}
