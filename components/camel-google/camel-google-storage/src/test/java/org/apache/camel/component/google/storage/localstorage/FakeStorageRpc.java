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
package org.apache.camel.component.google.storage.localstorage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.FileAlreadyExistsException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;

import com.google.api.client.util.DateTime;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.ServiceAccount;
import com.google.api.services.storage.model.StorageObject;
import com.google.cloud.Tuple;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.spi.v1.StorageRpc;
import com.google.cloud.storage.testing.StorageRpcTestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//this class has been extended from
//https://github.com/googleapis/java-storage-nio/blob/master/google-cloud-nio/src/main/java/com/google/cloud/storage/contrib/nio/testing/FakeStorageRpc.java
/**
 * A bare-bones in-memory implementation of StorageRpc, meant for testing.
 *
 * <p>
 * This class is <i>not</i> thread-safe. It's also (currently) limited in the following ways:
 *
 * <ul>
 * <li>Supported
 * <ul>
 * <li>object create
 * <li>object get
 * <li>object delete
 * <li>list the contents of a bucket
 * <li>generations
 * <li>NOW SUPPORTED bucket create
 * <li>NOW SUPPORTED bucket get
 * <li>NOW SUPPORTED bucket delete
 * <li>NOW SUPPORTED list all buckets
 * </ul>
 * <li>Unsupported
 * <ul>
 * <li>file attributes
 * <li>patch
 * <li>continueRewrite
 * <li>createBatch
 * <li>checksums, etags
 * <li>IAM operations
 * <li>BucketLock operations
 * <li>HMAC key operations
 * </ul>
 * </ul>
 */
@NotThreadSafe
class FakeStorageRpc extends StorageRpcTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(FakeStorageRpc.class);

    private static final SimpleDateFormat RFC_3339_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    // fullname -> metadata
    Map<String, StorageObject> metadata = new HashMap<>();
    // fullname -> contents
    Map<String, byte[]> contents = new HashMap<>();
    // fullname -> future contents that will be visible on close.
    Map<String, byte[]> futureContents = new HashMap<>();

    //Bucketname -> bucket
    Map<String, Bucket> buckets = new HashMap<>();

    private final boolean throwIfOption;

    /** @param throwIfOption if true, we throw when given any option */
    public FakeStorageRpc(boolean throwIfOption) {
        this.throwIfOption = throwIfOption;
    }

    // remove all files
    void reset() {
        LOG.info("reset");
        metadata = new HashMap<>();
        contents = new HashMap<>();
        buckets = new HashMap<>();
    }

    @Override
    public Bucket create(Bucket bucket, Map<Option, ?> options) {
        LOG.info("create_bucket: " + bucket.getName());
        buckets.put(bucket.getName(), bucket);
        return bucket;
    }

    @Override
    public StorageObject create(StorageObject object, InputStream content, Map<Option, ?> options)
            throws StorageException {
        potentiallyThrow(options);
        String key = fullname(object);
        object.setUpdated(now());
        metadata.put(key, object);
        try {
            contents.put(key, com.google.common.io.ByteStreams.toByteArray(content));
        } catch (IOException e) {
            throw new StorageException(e);
        }
        //  crc, etc
        return object;
    }

    @Override
    public Tuple<String, Iterable<Bucket>> list(Map<Option, ?> options) {
        String pageToken = null;
        String preprefix = "";
        //String delimiter = null;
        //long maxResults = Long.MAX_VALUE;
        for (Map.Entry<Option, ?> e : options.entrySet()) {
            switch (e.getKey()) {
                case PAGE_TOKEN:
                    pageToken = (String) e.getValue();
                    break;
                case PREFIX:
                    preprefix = (String) e.getValue();
                    if (preprefix.startsWith("/")) {
                        preprefix = preprefix.substring(1);
                    }
                    break;
                case DELIMITER:
                    //delimiter = (String) e.getValue();
                    break;
                case FIELDS:
                    // ignore and return all the fields
                    break;
                case MAX_RESULTS:
                    //maxResults = (Long) e.getValue();
                    break;
                case USER_PROJECT:
                    // prevent unsupported operation
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown option: " + e.getKey());
            }
        }

        Collection<Bucket> values = buckets.values();

        return Tuple.of(pageToken, (Iterable<Bucket>) values);
    }

    @Override
    public Tuple<String, Iterable<StorageObject>> list(String bucket, Map<Option, ?> options)
            throws StorageException {
        String delimiter = null;
        String preprefix = "";
        String pageToken = null;
        long maxResults = Long.MAX_VALUE;
        for (Map.Entry<Option, ?> e : options.entrySet()) {
            switch (e.getKey()) {
                case PAGE_TOKEN:
                    pageToken = (String) e.getValue();
                    break;
                case PREFIX:
                    preprefix = (String) e.getValue();
                    if (preprefix.startsWith("/")) {
                        preprefix = preprefix.substring(1);
                    }
                    break;
                case DELIMITER:
                    delimiter = (String) e.getValue();
                    break;
                case FIELDS:
                    // ignore and return all the fields
                    break;
                case MAX_RESULTS:
                    maxResults = (Long) e.getValue();
                    break;
                case USER_PROJECT:
                    // prevent unsupported operation
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown option: " + e.getKey());
            }
        }
        final String prefix = preprefix;

        List<StorageObject> values = new ArrayList<>();
        Map<String, StorageObject> folders = new HashMap<>();
        for (StorageObject so : metadata.values()) {
            if (!so.getBucket().equals(bucket) || !so.getName().startsWith(prefix)) {
                continue;
            }
            if (processedAsFolder(so, delimiter, prefix, folders)) {
                continue;
            }
            so.setSize(size(so));
            values.add(so);
        }
        values.addAll(folders.values());

        // truncate to max allowed length
        if (values.size() > maxResults) {
            List<StorageObject> newValues = new ArrayList<>();
            for (int i = 0; i < maxResults; i++) {
                newValues.add(values.get(i));
            }
            values = newValues;
        }

        // null cursor to indicate there is no more data (empty string would cause us to be called
        // again).
        // The type cast seems to be necessary to help Java's typesystem remember that collections are
        // iterable.
        return Tuple.of(pageToken, (Iterable<StorageObject>) values);
    }

    @Override
    public Bucket get(Bucket bucket, Map<Option, ?> options) {
        LOG.info("get_Bucket: {}", bucket.getName());
        return buckets.get(bucket.getName());
    }

    /** Returns the requested storage object or {@code null} if not found. */
    @Override
    public StorageObject get(StorageObject object, Map<Option, ?> options) throws StorageException {
        // we allow the "ID" option because we need to, but then we give a whole answer anyways
        // because the caller won't mind the extra fields.
        if (throwIfOption
                && !options.isEmpty()
                && options.size() > 1
                && options.keySet().toArray()[0] != Storage.BlobGetOption.fields(Storage.BlobField.ID)) {
            throw new UnsupportedOperationException();
        }

        String key = fullname(object);
        if (metadata.containsKey(key)) {
            StorageObject ret = metadata.get(key);
            ret.setSize(size(ret));
            ret.setId(key);

            return ret;
        }
        return null;
    }

    @Override
    public Bucket patch(Bucket bucket, Map<Option, ?> options) throws StorageException {
        potentiallyThrow(options);
        return null;
    }

    @Override
    public StorageObject patch(StorageObject storageObject, Map<Option, ?> options)
            throws StorageException {
        potentiallyThrow(options);
        return null;
    }

    @Override
    public boolean delete(Bucket bucket, Map<Option, ?> options) throws StorageException {
        String bucketName = bucket.getName();
        LOG.info("delete_bucket: {}", bucketName);

        if (buckets.containsKey(bucketName)) {
            buckets.remove(bucketName);
            return true;
        }
        return false;
    }

    @Override
    public boolean delete(StorageObject object, Map<Option, ?> options) throws StorageException {
        String key = fullname(object);
        contents.remove(key);
        return null != metadata.remove(key);
    }

    @Override
    public StorageObject compose(
            Iterable<StorageObject> sources, StorageObject target, Map<Option, ?> targetOptions)
            throws StorageException {
        return null;
    }

    @Override
    public byte[] load(StorageObject storageObject, Map<Option, ?> options) throws StorageException {
        String key = fullname(storageObject);
        if (!contents.containsKey(key)) {
            throw new StorageException(404, "File not found: " + key);
        }
        return contents.get(key);
    }

    @Override
    public Tuple<String, byte[]> read(
            StorageObject from, Map<Option, ?> options, long zposition, int zbytes)
            throws StorageException {
        // if non-null, then we check the file's at that generation.
        Long generationMatch = null;
        for (Option op : options.keySet()) {
            if (op.equals(StorageRpc.Option.IF_GENERATION_MATCH)) {
                generationMatch = (Long) options.get(op);
            } else {
                throw new UnsupportedOperationException("Unknown option: " + op);
            }
        }
        String key = fullname(from);
        if (!contents.containsKey(key)) {
            throw new StorageException(404, "File not found: " + key);
        }
        checkGeneration(key, generationMatch);
        long position = zposition;
        int bytes = zbytes;
        if (position < 0) {
            position = 0;
        }
        byte[] full = contents.get(key);
        if ((int) position + bytes > full.length) {
            bytes = full.length - (int) position;
        }
        if (bytes <= 0) {
            // special case: you're trying to read past the end
            return Tuple.of("etag-goes-here", new byte[0]);
        }
        byte[] ret = new byte[bytes];
        System.arraycopy(full, (int) position, ret, 0, bytes);
        return Tuple.of("etag-goes-here", ret);
    }

    @Override
    public long read(
            StorageObject from, Map<Option, ?> options, long position, OutputStream outputStream) {
        // if non-null, then we check the file's at that generation.
        Long generationMatch = null;
        for (Option op : options.keySet()) {
            if (op.equals(StorageRpc.Option.IF_GENERATION_MATCH)) {
                generationMatch = (Long) options.get(op);
            } else {
                throw new UnsupportedOperationException("Unknown option: " + op);
            }
        }
        String key = fullname(from);
        if (!contents.containsKey(key)) {
            throw new StorageException(404, "File not found: " + key);
        }
        checkGeneration(key, generationMatch);
        if (position < 0) {
            position = 0;
        }
        byte[] full = contents.get(key);
        int bytes = (int) (full.length - position);
        if (bytes <= 0) {
            // special case: you're trying to read past the end
            return 0;
        }
        try {
            outputStream.write(full, (int) position, bytes);
        } catch (IOException e) {
            throw new StorageException(500, "Failed to write to file", e);
        }
        return bytes;
    }

    @Override
    public String open(StorageObject object, Map<Option, ?> options) throws StorageException {
        String key = fullname(object);
        // if non-null, then we check the file's at that generation.
        Long generationMatch = null;
        for (Option option : options.keySet()) {
            // this is a bit of a hack, since we don't implement generations.
            if (option == Option.IF_GENERATION_MATCH) {
                generationMatch = (Long) options.get(option);
            }
        }
        checkGeneration(key, generationMatch);
        metadata.put(key, object);

        return fullname(object);
    }

    @Override
    public String open(String signedURL) {
        return null;
    }

    @Override
    public void write(
            String uploadId, byte[] toWrite, int toWriteOffset, long destOffset, int length, boolean last)
            throws StorageException {
        writeWithResponse(uploadId, toWrite, toWriteOffset, destOffset, length, last);
    }

    @Override
    public StorageObject writeWithResponse(
            String uploadId,
            byte[] toWrite,
            int toWriteOffset,
            long destOffset,
            int length,
            boolean last) {
        // this may have a lot more allocations than ideal, but it'll work.
        byte[] bytes;
        if (futureContents.containsKey(uploadId)) {
            bytes = futureContents.get(uploadId);
            if (bytes.length < length + destOffset) {
                byte[] newBytes = new byte[(int) (length + destOffset)];
                System.arraycopy(bytes, 0, newBytes, (int) 0, bytes.length);
                bytes = newBytes;
            }
        } else {
            bytes = new byte[(int) (length + destOffset)];
        }
        System.arraycopy(toWrite, toWriteOffset, bytes, (int) destOffset, length);
        // we want to mimic the GCS behavior that file contents are only visible on close.
        StorageObject storageObject = null;
        if (last) {
            contents.put(uploadId, bytes);
            futureContents.remove(uploadId);
            if (metadata.containsKey(uploadId)) {
                storageObject = metadata.get(uploadId);
                storageObject.setUpdated(now());
                Long generation = storageObject.getGeneration();
                if (null == generation) {
                    generation = Long.valueOf(0);
                }
                storageObject.setGeneration(++generation);
                metadata.put(uploadId, storageObject);
            }
        } else {
            futureContents.put(uploadId, bytes);
        }
        return storageObject;
    }

    @Override
    public RewriteResponse openRewrite(RewriteRequest rewriteRequest) throws StorageException {
        String sourceKey = fullname(rewriteRequest.source);

        // a little hackish, just good enough for the tests to work.
        if (!contents.containsKey(sourceKey)) {
            throw new StorageException(404, "File not found: " + sourceKey);
        }

        // if non-null, then we check the file's at that generation.
        Long generationMatch = null;
        for (Option option : rewriteRequest.targetOptions.keySet()) {
            // this is a bit of a hack, since we don't implement generations.
            if (option == Option.IF_GENERATION_MATCH) {
                generationMatch = (Long) rewriteRequest.targetOptions.get(option);
            }
        }

        String destKey = fullname(rewriteRequest.target);

        // if this is a new file, set generation to 1, else increment the existing generation
        long generation = 1;
        if (metadata.containsKey(destKey)) {
            generation = metadata.get(destKey).getGeneration() + 1;
        }

        checkGeneration(destKey, generationMatch);

        byte[] data = contents.get(sourceKey);

        rewriteRequest.target.setGeneration(generation);
        rewriteRequest.target.setSize(BigInteger.valueOf(data.length));
        rewriteRequest.target.setUpdated(metadata.get(sourceKey).getUpdated());

        metadata.put(destKey, rewriteRequest.target);

        contents.put(destKey, Arrays.copyOf(data, data.length));
        return new RewriteResponse(
                rewriteRequest,
                rewriteRequest.target,
                data.length,
                true,
                "rewriteToken goes here",
                data.length);
    }

    private static DateTime now() {
        return DateTime.parseRfc3339(RFC_3339_FORMATTER.format(new Date()));
    }

    private String fullname(StorageObject so) {
        return so.getBucket() + "/" + so.getName();
    }

    private BigInteger size(StorageObject so) {
        String key = fullname(so);

        if (contents.containsKey(key)) {
            return BigInteger.valueOf(contents.get(key).length);
        }

        return null;
    }

    private void potentiallyThrow(Map<Option, ?> options) throws UnsupportedOperationException {
        if (throwIfOption && !options.isEmpty()) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Throw if we're asking for generation 0 and the file exists, or if the requested generation number doesn't match
     * what is asked.
     *
     * @param key
     * @param generationMatch
     */
    private void checkGeneration(String key, Long generationMatch) {
        if (null == generationMatch) {
            return;
        }
        if (generationMatch == 0 && metadata.containsKey(key)) {
            throw new StorageException(new FileAlreadyExistsException(key));
        }
        if (generationMatch != 0) {
            Long generation = metadata.get(key).getGeneration();
            if (!generationMatch.equals(generation)) {
                throw new StorageException(
                        404, "Generation mismatch. Requested " + generationMatch + " but got " + generation);
            }
        }
    }

    // Returns true if this is a folder. Adds it to folders if it isn't already there.
    private static boolean processedAsFolder(
            StorageObject so,
            String delimiter,
            String prefix, /* inout */
            Map<String, StorageObject> folders) {
        if (delimiter == null) {
            return false;
        }
        int nextSlash = so.getName().indexOf(delimiter, prefix.length());
        if (nextSlash < 0) {
            return false;
        }
        String folderName = so.getName().substring(0, nextSlash + 1);
        if (folders.containsKey(folderName)) {
            return true;
        }
        StorageObject fakeFolder = new StorageObject();
        fakeFolder.setName(folderName);
        fakeFolder.setBucket(so.getBucket());
        fakeFolder.setGeneration(so.getGeneration());
        fakeFolder.set("isDirectory", true);
        fakeFolder.setSize(BigInteger.ZERO);
        folders.put(folderName, fakeFolder);
        return true;
    }

    @Override
    public ServiceAccount getServiceAccount(String projectId) {
        return null;
    }
}
