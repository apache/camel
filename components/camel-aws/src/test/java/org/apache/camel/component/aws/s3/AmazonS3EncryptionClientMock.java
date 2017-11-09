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
package org.apache.camel.component.aws.s3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3EncryptionClient;
import com.amazonaws.services.s3.S3ResponseMetadata;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.BucketLoggingConfiguration;
import com.amazonaws.services.s3.model.BucketNotificationConfiguration;
import com.amazonaws.services.s3.model.BucketPolicy;
import com.amazonaws.services.s3.model.BucketVersioningConfiguration;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.CopyObjectResult;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.DeleteBucketRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.DeleteVersionRequest;
import com.amazonaws.services.s3.model.EncryptionMaterials;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectMetadataRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ListBucketsRequest;
import com.amazonaws.services.s3.model.ListMultipartUploadsRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListPartsRequest;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.MultipartUploadListing;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.Owner;
import com.amazonaws.services.s3.model.PartListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.Region;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.SetBucketLoggingConfigurationRequest;
import com.amazonaws.services.s3.model.SetBucketVersioningConfigurationRequest;
import com.amazonaws.services.s3.model.StaticEncryptionMaterialsProvider;
import com.amazonaws.services.s3.model.StorageClass;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;
import com.amazonaws.services.s3.model.VersionListing;

import org.apache.camel.util.ObjectHelper;
import org.junit.Assert;

public class AmazonS3EncryptionClientMock extends AmazonS3EncryptionClient {
    
    List<S3Object> objects = new CopyOnWriteArrayList<S3Object>();
    List<PutObjectRequest> putObjectRequests = new CopyOnWriteArrayList<PutObjectRequest>();
    
    private boolean nonExistingBucketCreated;
    
    private int maxCapacity = 100;
    
    public AmazonS3EncryptionClientMock() {
        super(new StaticEncryptionMaterialsProvider(new EncryptionMaterials(new KeyPair(null, null))));
    }

    @Override
    public VersionListing listNextBatchOfVersions(VersionListing previousVersionListing) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionListing listVersions(String bucketName, String prefix) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionListing listVersions(String bucketName, String prefix, String keyMarker, String versionIdMarker, String delimiter, Integer maxKeys) 
        throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionListing listVersions(ListVersionsRequest listVersionsRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectListing listObjects(String bucketName) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectListing listObjects(String bucketName, String prefix) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectListing listObjects(ListObjectsRequest listObjectsRequest) throws AmazonClientException, AmazonServiceException {
        if ("nonExistingBucket".equals(listObjectsRequest.getBucketName()) && !nonExistingBucketCreated) {
            AmazonServiceException ex = new AmazonServiceException("Unknown bucket");
            ex.setStatusCode(404);
            throw ex; 
        }
        int capacity;
        ObjectListing objectListing = new ObjectListing();
        if (!ObjectHelper.isEmpty(listObjectsRequest.getMaxKeys()) && listObjectsRequest.getMaxKeys() != null) {
            capacity = listObjectsRequest.getMaxKeys();
        } else {
            capacity = maxCapacity;
        }
        
        for (int index = 0; index < objects.size() && index < capacity; index++) {
            S3ObjectSummary s3ObjectSummary = new S3ObjectSummary();
            s3ObjectSummary.setBucketName(objects.get(index).getBucketName());
            s3ObjectSummary.setKey(objects.get(index).getKey());
            
            objectListing.getObjectSummaries().add(s3ObjectSummary);
        }

        return objectListing;
    }

    @Override
    public ObjectListing listNextBatchOfObjects(ObjectListing previousObjectListing) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Owner getS3AccountOwner() throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Bucket> listBuckets(ListBucketsRequest listBucketsRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Bucket> listBuckets() throws AmazonClientException, AmazonServiceException {
        ArrayList<Bucket> list = new ArrayList<Bucket>();
        Bucket bucket = new Bucket("camel-bucket");
        bucket.setOwner(new Owner("Camel", "camel"));
        bucket.setCreationDate(new Date());
        list.add(bucket);
        return list;
    }

    @Override
    public String getBucketLocation(String bucketName) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bucket createBucket(String bucketName) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bucket createBucket(String bucketName, Region region) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bucket createBucket(String bucketName, String region) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bucket createBucket(CreateBucketRequest createBucketRequest) throws AmazonClientException, AmazonServiceException {
        if ("nonExistingBucket".equals(createBucketRequest.getBucketName())) {
            nonExistingBucketCreated = true; 
        }
        
        Bucket bucket = new Bucket();
        bucket.setName(createBucketRequest.getBucketName());
        bucket.setCreationDate(new Date());
        bucket.setOwner(new Owner("c2efc7302b9011ba9a78a92ac5fd1cd47b61790499ab5ddf5a37c31f0638a8fc ", "Christian Mueller"));
        return bucket;
    }

    @Override
    public AccessControlList getObjectAcl(String bucketName, String key) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccessControlList getObjectAcl(String bucketName, String key, String versionId) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObjectAcl(String bucketName, String key, AccessControlList acl) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObjectAcl(String bucketName, String key, CannedAccessControlList acl) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObjectAcl(String bucketName, String key, String versionId, AccessControlList acl) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObjectAcl(String bucketName, String key, String versionId, CannedAccessControlList acl) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccessControlList getBucketAcl(String bucketName) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketAcl(String bucketName, AccessControlList acl) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketAcl(String bucketName, CannedAccessControlList acl) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectMetadata getObjectMetadata(String bucketName, String key) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectMetadata getObjectMetadata(GetObjectMetadataRequest getObjectMetadataRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }
    
    
    @Override
    public S3Object getObject(String bucketName, String key) throws AmazonClientException, AmazonServiceException {
        for (S3Object s3Object : objects) {
            if (bucketName.equals(s3Object.getBucketName()) && key.equals(s3Object.getKey())) {
                return s3Object;
            }
        }
        
        return null;
    }

    @Override
    public boolean doesBucketExist(String bucketName) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void changeObjectStorageClass(String bucketName, String key, StorageClass newStorageClass) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public S3Object getObject(GetObjectRequest getObjectRequest) throws AmazonClientException, AmazonServiceException {
        return getObject(getObjectRequest.getBucketName(), getObjectRequest.getKey());
    }

    @Override
    public ObjectMetadata getObject(GetObjectRequest getObjectRequest, File destinationFile) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucket(String bucketName) throws AmazonClientException, AmazonServiceException {
        // noop
    }

    @Override
    public void deleteBucket(DeleteBucketRequest deleteBucketRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PutObjectResult putObject(String bucketName, String key, File file) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PutObjectResult putObject(String bucketName, String key, InputStream input, ObjectMetadata metadata) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("resource")
    @Override
    public PutObjectResult putObject(PutObjectRequest putObjectRequest) throws AmazonClientException, AmazonServiceException {
        putObjectRequests.add(putObjectRequest);
        
        S3Object s3Object = new S3Object();
        s3Object.setBucketName(putObjectRequest.getBucketName());
        s3Object.setKey(putObjectRequest.getKey());
        if (putObjectRequest.getFile() != null) {
            try {
                s3Object.setObjectContent(new FileInputStream(putObjectRequest.getFile()));
            } catch (FileNotFoundException e) {
                throw new AmazonServiceException("Cannot store the file object.", e);
            }
        } else {
            s3Object.setObjectContent(putObjectRequest.getInputStream());
        }
        objects.add(s3Object);
        
        PutObjectResult putObjectResult = new PutObjectResult();
        putObjectResult.setETag("3a5c8b1ad448bca04584ecb55b836264");
        return putObjectResult;
    }

    @Override
    public CopyObjectResult copyObject(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public CopyObjectResult copyObject(CopyObjectRequest copyObjectRequest) throws AmazonClientException, AmazonServiceException {
        CopyObjectResult copyObjectResult = new CopyObjectResult();
        copyObjectResult.setETag("3a5c8b1ad448bca04584ecb55b836264");
        copyObjectResult.setVersionId("11192828ahsh2723");
        return copyObjectResult;
    }

    @Override
    public void deleteObject(String bucketName, String key) throws AmazonClientException, AmazonServiceException {
        //noop
    }

    @Override
    public void deleteObject(DeleteObjectRequest deleteObjectRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteVersion(String bucketName, String key, String versionId) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteVersion(DeleteVersionRequest deleteVersionRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketVersioningConfiguration(SetBucketVersioningConfigurationRequest setBucketVersioningConfigurationRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketVersioningConfiguration getBucketVersioningConfiguration(String bucketName) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketNotificationConfiguration(String bucketName, BucketNotificationConfiguration bucketNotificationConfiguration) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketNotificationConfiguration getBucketNotificationConfiguration(String bucketName) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketLoggingConfiguration getBucketLoggingConfiguration(String bucketName) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketLoggingConfiguration(SetBucketLoggingConfigurationRequest setBucketLoggingConfigurationRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketPolicy getBucketPolicy(String bucketName) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketPolicy(String bucketName, String policyText) throws AmazonClientException, AmazonServiceException {
        Assert.assertEquals("nonExistingBucket", bucketName);
        Assert.assertEquals("xxx", policyText);
    }

    @Override
    public void deleteBucketPolicy(String bucketName) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public URL generatePresignedUrl(String bucketName, String key, Date expiration) throws AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public URL generatePresignedUrl(String bucketName, String key, Date expiration, HttpMethod method) throws AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public URL generatePresignedUrl(GeneratePresignedUrlRequest generatePresignedUrlRequest) throws AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void abortMultipartUpload(AbortMultipartUploadRequest abortMultipartUploadRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompleteMultipartUploadResult completeMultipartUpload(CompleteMultipartUploadRequest completeMultipartUploadRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InitiateMultipartUploadResult initiateMultipartUpload(InitiateMultipartUploadRequest initiateMultipartUploadRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public MultipartUploadListing listMultipartUploads(ListMultipartUploadsRequest listMultipartUploadsRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PartListing listParts(ListPartsRequest listPartsRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadPartResult uploadPart(UploadPartRequest uploadPartRequest) throws AmazonClientException, AmazonServiceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public S3ResponseMetadata getCachedResponseMetadata(AmazonWebServiceRequest request) {
        throw new UnsupportedOperationException();
    }
}