package org.apache.camel.component.aws.s3.client.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3EncryptionClient;
import com.amazonaws.services.s3.model.EncryptionMaterials;
import org.apache.camel.component.aws.s3.S3Configuration;
import org.junit.Assert;
import org.junit.Test;

/**
 * Basic testing to ensure that the StandardAWSS3ClientImpl class is returning a standard client that is
 * capable of encryption given certain parameters. These clients have been in existence for a long time, but haven't
 * been properly unit tested.
 */
public class StandardAWSS3ClientImplTest {
	private static final int maxConnections = 1;
	private EncryptionMaterials encryptionMaterials = mock(EncryptionMaterials.class);

	@Test
	public void standardAWSS3ClientImplNoEncryption() {
		StandardAWSS3ClientImpl standardAWSS3Client = new StandardAWSS3ClientImpl(getS3ConfigurationNoEncryption(), maxConnections);
		AmazonS3 s3Client = standardAWSS3Client.getS3Client();
		Assert.assertNotNull(s3Client);
		Assert.assertFalse(s3Client instanceof AmazonS3EncryptionClient);
	}

	@Test
	public void standardAWSS3ClientImplUseEncryption() {
		StandardAWSS3ClientImpl standardAWSS3Client = new StandardAWSS3ClientImpl(getS3ConfigurationUseEncryption(), maxConnections);
		AmazonS3 s3Client = standardAWSS3Client.getS3Client();
		Assert.assertNotNull(s3Client);
		Assert.assertTrue(s3Client instanceof AmazonS3EncryptionClient);
	}

	private S3Configuration getS3ConfigurationNoEncryption() {
		S3Configuration s3Configuration = mock(S3Configuration.class);
		when(s3Configuration.isUseEncryption()).thenReturn(false);
		return s3Configuration;
	}

	private S3Configuration getS3ConfigurationUseEncryption() {
		S3Configuration s3Configuration = mock(S3Configuration.class);
		when(s3Configuration.isUseEncryption()).thenReturn(true);
		return s3Configuration;
	}
}
