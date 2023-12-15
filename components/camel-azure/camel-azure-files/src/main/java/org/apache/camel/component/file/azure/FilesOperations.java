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
package org.apache.camel.component.file.azure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.EmptyStackException;
import java.util.Stack;

import com.azure.core.util.Context;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.file.share.ShareDirectoryClient;
import com.azure.storage.file.share.ShareFileClient;
import com.azure.storage.file.share.ShareServiceClient;
import com.azure.storage.file.share.ShareServiceClientBuilder;
import com.azure.storage.file.share.models.ShareFileItem;
import com.azure.storage.file.share.models.ShareFileRange;
import com.azure.storage.file.share.models.ShareStorageException;
import com.azure.storage.file.share.options.ShareDirectoryCreateOptions;
import com.azure.storage.file.share.options.ShareFileRenameOptions;
import com.azure.storage.file.share.options.ShareListFilesAndDirectoriesOptions;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.RemoteFile;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Operations with locally tracked cwd state.
 * <p>
 * The state limits thread safety.
 */
public class FilesOperations extends NormalizedOperations {

    // TODO the underlying lib supports multi-step navigation, could we eliminate
    // cwd state?

    public static final String HTTPS = "https";

    static final int HTTP_OK = 200;
    static final int HTTP_CREATED = 201;
    static final int HTTP_ACCEPTED = 202;
    static final int HTTP_NOT_FOUND = 404;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final FilesEndpoint endpoint;
    private final FilesConfiguration configuration;
    private final FilesToken token;
    private ShareServiceClient client;
    private ShareDirectoryClient root;
    private Stack<ShareDirectoryClient> dirStack = new Stack<>();

    FilesOperations(FilesEndpoint endpoint) {
        super(endpoint.getConfiguration());
        this.endpoint = endpoint;
        configuration = endpoint.getConfiguration();
        token = endpoint.getToken();
    }

    @Override
    public void setEndpoint(GenericFileEndpoint<ShareFileItem> endpoint) {
        if (this.endpoint != endpoint) {
            throw new IllegalStateException("The endpoint is final: " + endpoint);
        }
    }

    @Override
    public GenericFile<ShareFileItem> newGenericFile() {
        log.trace("newGenericFile()");
        return new RemoteFile<>();
    }

    @Override
    public boolean connect(RemoteFileConfiguration config, Exchange exchange)
            throws GenericFileOperationFailedException {
        log.trace("connect()");
        root = getClient().getShareClient(configuration.getShare()).getRootDirectoryClient();
        // TODO what about (starting) directory as the root?
        dirStack.push(root);
        // TODO translate runtime exception to Camel one?
        return existsRemote(root);
    }

    @Override
    public boolean isConnected() {
        log.trace("isConnected()");
        return root != null;
    }

    @Override
    public void disconnect() {
        log.trace("disconnect()");
    }

    @Override
    public void forceDisconnect() throws GenericFileOperationFailedException {
        log.debug("forceDisconnect()");
        var ms = configuration.getConnectTimeout();
        root.forceCloseAllHandles(true, Duration.ofMillis(ms), Context.NONE);
        root = null;
        dirStack = new Stack<>();
    }

    @Override
    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        log.trace("deleteFile({})", name);

        reconnectIfNecessary(null);

        var backup = backup();
        try {
            changeCurrentDirectory(FileUtil.onlyPath(name));
            return deleteRemote(cwd(), FileUtil.stripPath(name));
        } finally {
            restore(backup);
        }
    }

    private boolean deleteRemote(ShareDirectoryClient dirClient, String fileName) {
        log.trace("{}> rm {}", dirClient.getDirectoryPath(), fileName);
        var status = dirClient
                .deleteFileIfExistsWithResponse(fileName, endpoint.getMetadataTimeout(), Context.NONE).getStatusCode();
        // doc: If Response's status code is 202, the file was successfully deleted. If status code is 404, the file does not exist.
        return status == HTTP_NOT_FOUND || status == HTTP_ACCEPTED;
    }

    @SuppressWarnings("unchecked")
    void restore(Object backup) {
        dirStack = (Stack<ShareDirectoryClient>) backup;
    }

    Object backup() {
        return dirStack.clone();
    }

    private ShareDirectoryClient cwd() {
        var cwd = dirStack.peek();
        return cwd;
    }

    @Override
    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        // by observation both paths are absolute paths on the share
        log.trace("renameFile({}, {})", from, to);

        try {
            return renameRemote(getFileClient(from), FilesPath.ensureRelative(to));
        } catch (RuntimeException e) {
            throw new GenericFileOperationFailedException("Cannot rename: " + from + " to: " + to, e);
        }
    }

    private boolean renameRemote(ShareFileClient fileClient, String shareRelativeTo) {
        var options = new ShareFileRenameOptions(shareRelativeTo);
        // known strategies try to remove an existing target file before calling rename
        // but it is neither atomic nor sure so instruct Azure Files to overwrite the file
        options.setReplaceIfExists(Boolean.TRUE);
        var renamed = fileClient.renameWithResponse(options, endpoint.getMetadataTimeout(), Context.NONE).getValue();
        return existsRemote(renamed);
    }

    @Override
    public boolean buildDirectory(String directory) throws GenericFileOperationFailedException {

        boolean success = existsDirectory(directory);

        if (!success) {
            success = buildDirectoryStepByStep(directory);
        }

        return success;
    }

    private boolean buildDirectoryStepByStep(String dir) {
        if (FilesPath.isRoot(dir)) {
            return true;
        }
        var steps = FilesPath.split(dir);
        var stepClient = root;
        for (var step : steps) {
            stepClient = buildDirectoryRemote(stepClient, step);
            if (!existsRemote(stepClient)) {
                return false;
            }
        }

        return true;
    }

    private ShareDirectoryClient buildDirectoryRemote(ShareDirectoryClient dirClient, String name) {
        log.trace("{}> mkdir {}", dirClient.getDirectoryPath(), name);
        var options = new ShareDirectoryCreateOptions();
        return dirClient
                .createSubdirectoryIfNotExistsWithResponse(name, options, endpoint.getMetadataTimeout(), Context.NONE)
                .getValue();
    }

    @Override
    public boolean retrieveFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        log.trace("retrieveFile({})", name);
        boolean answer;
        var backup = backup();
        try {
            if (endpoint.getLocalWorkDirectory() != null) {
                answer = retrieveFileToFileInLocalWorkDirectory(name, exchange, endpoint.isResumeDownload());
            } else {
                answer = retrieveFileToBody(name, exchange);
            }
        } finally {
            restore(backup);
        }

        return answer;
    }

    @Override
    public void releaseRetrievedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
        log.trace("releaseRetrievedFileResources({})", exchange.getExchangeId());
        var is = exchange.getIn().getHeader(FilesHeaders.REMOTE_FILE_INPUT_STREAM, InputStream.class);

        if (is != null) {
            IOHelper.close(is);
        }
    }

    @SuppressWarnings({ "unchecked", "resource" })
    private boolean retrieveFileToBody(String name, Exchange exchange) throws GenericFileOperationFailedException {
        boolean success = false;
        GenericFile<ShareFileItem> target = (GenericFile<ShareFileItem>) exchange
                .getProperty(FileComponent.FILE_EXCHANGE_FILE);
        org.apache.camel.util.ObjectHelper.notNull(target,
                "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");

        String path = FileUtil.onlyPath(name);
        if (path != null) {
            changeCurrentDirectory(path);
        }
        // remote name is now only the file name as we just changed
        // directory
        String remoteName = FileUtil.stripPath(name);

        if (configuration.isStreamDownload()) {
            log.trace("Prepared {} for download as opened input stream.", remoteName);
            InputStream is = cwd().getFileClient(remoteName).openInputStream();
            target.setBody(is);
            exchange.getIn().setHeader(FilesHeaders.REMOTE_FILE_INPUT_STREAM, is);
            success = true;
        } else {
            log.trace("Downloading {} to byte[] body.", remoteName);
            var os = new ByteArrayOutputStream();
            ShareFileRange range = new ShareFileRange(0);
            var ret = cwd().getFileClient(remoteName).downloadWithResponse(os, range, null, endpoint.getDataTimeout(),
                    Context.NONE);
            success = ret.getStatusCode() == HTTP_OK;
            IOHelper.close(os);
            target.setBody(os.toByteArray());
        }

        return success;
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToFileInLocalWorkDirectory(String name, Exchange exchange, boolean resumeDownload)
            throws GenericFileOperationFailedException {
        File inProgress;
        File local = new File(FileUtil.normalizePath(endpoint.getLocalWorkDirectory()));
        OutputStream os;
        long existingSize = -1;

        try {
            // use relative filename in local work directory
            GenericFile<ShareFileItem> target = (GenericFile<ShareFileItem>) exchange
                    .getProperty(FileComponent.FILE_EXCHANGE_FILE);
            org.apache.camel.util.ObjectHelper.notNull(target,
                    "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");
            String relativeName = target.getRelativeFilePath();

            inProgress = new File(local, relativeName + ".inprogress");
            local = new File(local, relativeName);

            // create directory to local work file
            boolean result = local.mkdirs();
            if (!result) {
                log.warn(
                        "Failed to create local directory {} while retrieving file in local work directory. Directory may already exist or have been created externally",
                        local);
            }

            // delete any local file (as its the temp file that is in the
            // in-progress download)
            if (local.exists()) {
                if (!FileUtil.deleteFile(local)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + local);
                }
            }

            // if a previous file exists then store its current size as its a
            // partial download
            boolean exists = inProgress.exists();
            if (exists) {
                existingSize = inProgress.length();
            }

            // if we do not resume download, then delete any existing temp file
            // and create a new to use for in-progress download
            if (!resumeDownload) {
                if (exists && !FileUtil.deleteFile(inProgress)) {
                    throw new GenericFileOperationFailedException(
                            "Cannot delete existing local work file: " + inProgress);
                }
                if (!inProgress.createNewFile()) {
                    throw new GenericFileOperationFailedException("Cannot create new local work file: " + inProgress);
                }
            }

            // store content as a file in the local work directory in the temp
            // handle
            boolean append = resumeDownload && existingSize > 0;
            os = new FileOutputStream(inProgress, append);

            // set header with the path to the local work file
            exchange.getIn().setHeader(FilesHeaders.FILE_LOCAL_WORK_PATH, local.getPath());

        } catch (Exception e) {
            throw new GenericFileOperationFailedException("Cannot create new local work file: " + local, e);
        }

        boolean result;
        try {
            GenericFile<ShareFileItem> target = (GenericFile<ShareFileItem>) exchange
                    .getProperty(FileComponent.FILE_EXCHANGE_FILE);
            // store the java.io.File handle as the body
            target.setBody(local);

            String path = FileUtil.onlyPath(name);
            if (path != null) {
                changeCurrentDirectory(path);
            }
            String remoteName = FileUtil.stripPath(name);

            ShareFileRange range = new ShareFileRange(0);
            // the file exists so lets try to resume the download
            if (resumeDownload && existingSize > 0) {
                log.trace("Client restartOffset: {}", existingSize);
                log.debug("Resuming download of file: {} at position: {}", remoteName, existingSize);
                range = new ShareFileRange(existingSize);
            }
            log.trace("Downloading {} to local work directory.", remoteName);
            var ret = cwd().getFileClient(remoteName).downloadWithResponse(os, range, null, endpoint.getDataTimeout(),
                    Context.NONE);
            result = ret.getStatusCode() == HTTP_OK;

        } catch (RuntimeException e) {
            log.trace("Error occurred during retrieving file: {} to local directory.", name);
            // if we do not attempt to resume download, then attempt to delete
            // the temporary file
            if (!resumeDownload) {
                log.trace("Deleting local work file: {}", name);
                // failed to retrieve the file so we need to close streams and
                // delete in progress file
                // must close stream before deleting file
                IOHelper.close(os, "retrieve: " + name, log);
                boolean deleted = FileUtil.deleteFile(inProgress);
                if (!deleted) {
                    log.warn(
                            "Error occurred during retrieving file: {} to local directory. Cannot delete local work file: {}",
                            inProgress, name);
                }
            }
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        } finally {
            // need to close the stream before rename it
            IOHelper.close(os, "retrieve: " + name, log);
        }

        log.debug("Retrieve file to local work file result: {}", result);

        if (result) {
            log.trace("Renaming local in progress file from: {} to: {}", inProgress, local);
            // operation went okay so rename temp to local after we have
            // retrieved the data
            try {
                if (!FileUtil.renameFile(inProgress, local, false)) {
                    throw new GenericFileOperationFailedException(
                            "Cannot rename local work file from: " + inProgress + " to: " + local);
                }
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        "Cannot rename local work file from: " + inProgress + " to: " + local, e);
            }
        }

        return result;
    }

    @Override
    public boolean storeFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        log.trace("storeFile({},,{})", name, size);

        name = configuration.normalizePath(name);

        boolean answer;
        String path = FileUtil.onlyPath(name);
        String targetName = name;
        var backup = backup();

        try {
            if (path != null) {
                changeCurrentDirectory(path);
                targetName = FileUtil.stripPath(name);
            }
            answer = storeFile(name, targetName, exchange);
        } catch (GenericFileOperationFailedException e) {
            throw e;
        } finally {
            restore(backup);
        }

        return answer;
    }

    private boolean storeFile(String name, String targetName, Exchange exchange)
            throws GenericFileOperationFailedException {
        boolean existFile = false;
        // if an existing file already exists what should we do?
        if (endpoint.getFileExist() == GenericFileExist.Ignore || endpoint.getFileExist() == GenericFileExist.Fail) {
            existFile = existsFile(targetName);
            if (existFile && endpoint.getFileExist() == GenericFileExist.Ignore) {
                // ignore but indicate that the file was written
                log.trace("An existing file already exists: {}. Ignore and do not override it.", name);
                return true;
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Fail) {
                throw new GenericFileOperationFailedException(
                        "File already exist: " + name + ". Cannot write new file.");
            }
        }

        InputStream is = null;
        int length = 0;
        if (exchange.getIn().getBody() == null) {
            // Do an explicit test for a null body and decide what to do
            if (endpoint.isAllowNullBody()) {
                log.trace("Writing empty file.");
                is = new ByteArrayInputStream(new byte[] {});
            } else {
                throw new GenericFileOperationFailedException("Cannot write null body to file: " + name);
            }
        }

        try {
            if (is == null) {
                var knownLength = exchange.getIn().getHeader(Exchange.FILE_LENGTH, Long.class);
                if (knownLength != null) {
                    is = exchange.getIn().getMandatoryBody(InputStream.class);
                    length = knownLength.intValue();
                } else {
                    log.warn("No file length header, so converting body to byte[].  It might be memory intensive.");
                    var bytes = exchange.getIn().getMandatoryBody(byte[].class);
                    length = bytes.length;
                    is = new ByteArrayInputStream(bytes);
                }
            }

            final StopWatch watch = new StopWatch();
            boolean answer = false;
            log.debug("About to store file: {} length: {} using stream: {}", targetName, length, is);
            if (existFile && endpoint.getFileExist() == GenericFileExist.Append) {
                assert false; // rejected by options validation
            } else {
                var cwd = cwd();
                var file = cwd.getFileClient(targetName);
                if (deleteRemote(cwd, targetName) && createRemote(file, length)) {
                    storeRemote(file, is);
                    answer = true;
                }
            }
            if (log.isDebugEnabled()) {
                long time = watch.taken();
                log.debug("Took {} ({} millis) to store: {} and files client returned: {}",
                        TimeUtils.printDuration(time, true), time, targetName, answer);
            }

            return answer;

        } catch (InvalidPayloadException | IOException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } finally {
            IOHelper.close(is, "store: " + name, log);
        }
    }

    private void storeRemote(ShareFileClient file, InputStream is) throws IOException {
        log.trace("> put {}", file.getFilePath());
        // NOTE: here >4MiB is possible (unlike the upload limitation)
        try (var os = file.getFileOutputStream()) {
            // TODO add data timeout?
            // TODO err if the is is shorter than allocated file length?
            is.transferTo(os);
            os.flush();
        }
    }

    private boolean createRemote(ShareFileClient fileClient, int length) {
        var code = fileClient.createWithResponse(length, null, null, null, null,
                endpoint.getMetadataTimeout(), Context.NONE).getStatusCode();
        return code == HTTP_CREATED || code == HTTP_OK;
    }

    @Override
    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        log.trace("existsFile({})", name);
        return existsRemote(getFileClient(name));
    }

    private boolean existsDirectory(String path) {
        return existsRemote(getDirClient(path));
    }

    private boolean existsRemote(ShareDirectoryClient dirClient) {
        try {
            return Boolean.TRUE.equals(
                    dirClient.existsWithResponse(endpoint.getMetadataTimeout(), Context.NONE).getValue());
        } catch (ShareStorageException ex) {
            // observed "Status code 404, ParentNotFound" for deep checks
            if (ex.getStatusCode() == HTTP_NOT_FOUND) {
                return false;
            }
            throw ex;
        }
    }

    private boolean existsRemote(ShareFileClient fileClient) {
        try {
            return Boolean.TRUE
                    .equals(fileClient.existsWithResponse(endpoint.getMetadataTimeout(), Context.NONE).getValue());
        } catch (ShareStorageException ex) {
            // observed "Status code 404, ParentNotFound" for deep checks
            if (ex.getStatusCode() == HTTP_NOT_FOUND) {
                return false;
            }
            throw ex;
        }
    }

    /**
     * @return a relative path, from the share root, of the current directory
     */
    @Override
    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        String answer = cwd().getDirectoryPath();
        log.trace("getCurrentDirectory(): {}", answer);
        return answer;
    }

    @Override
    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        log.trace("changeCurrentDirectory({})", path);
        if (FilesPath.isEmpty(path) || path.equals(FilesPath.CWD)
                || path.equals(FilesPath.SHARE_ROOT + cwd().getDirectoryPath())) {
            return;
        }

        var dirs = FilesPath.splitToSteps(path, true);
        for (String dir : dirs) {
            trivialCd(dir);
        }
    }

    private void trivialCd(String pathStep) {
        if (FilesPath.isEmptyStep(pathStep)) {
            return;
        }

        var cwd = cwd();
        log.trace("{}> cd {}", cwd.getDirectoryPath(), pathStep);
        boolean success;
        try {
            if (FilesPath.SHARE_ROOT.equals(pathStep)) {
                changeToRoot();
                success = true;
            } else if (FilesPath.PARENT.equals(pathStep)) {
                changeToParentDirectory();
                success = true;
            } else {
                var subDir = cwd.getSubdirectoryClient(pathStep);
                success = existsRemote(subDir);
                if (success) {
                    dirStack.push(subDir);
                }
            }
        } catch (RuntimeException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        }
        if (!success) {
            throw new GenericFileOperationFailedException("Cannot cd(" + pathStep + ").");
        }
    }

    @Override
    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        log.trace("changeToParentDirectory()");
        try {
            dirStack.pop();
        } catch (EmptyStackException e) {
            throw new GenericFileOperationFailedException("Root dir does not have parent.", e);
        }
    }

    void changeToRoot() {
        if (!isConnected()) {
            throw new GenericFileOperationFailedException("Cannot cd to the share root: not connected");
        }
        dirStack.empty();
        dirStack.push(root);
    }

    @Override
    public ShareFileItem[] listFiles() throws GenericFileOperationFailedException {
        log.trace("listFiles()");
        return listRemote(cwd());
    }

    @Override
    public ShareFileItem[] listFiles(String path) throws GenericFileOperationFailedException {
        log.trace("listFiles({})", path);
        return listRemote(getDirClient(path));
    }

    private ShareDirectoryClient getDirClient(String path) {

        assert FilesPath.isAbsolute(path);

        if (FilesPath.isRoot(path)) {
            return root;
        }
        var dir = FilesPath.ensureRelative(path);
        return root.getSubdirectoryClient(dir);
    }

    private ShareFileClient getFileClient(String path) {

        assert FilesPath.isAbsolute(path) : "Expecting /a_path from share root, got: " + path;
        assert !FilesPath.isRoot(path) : "Expecting /a_path from share root, got: " + path;
        // if we got a relative path we need to figure out its base

        return root.getFileClient(FilesPath.ensureRelative(path));
    }

    private ShareFileItem[] listRemote(ShareDirectoryClient dir) {
        log.trace("{}> ls -a", dir.getDirectoryPath());
        try {
            var withTS = new ShareListFilesAndDirectoriesOptions().setIncludeTimestamps(true);
            return dir.listFilesAndDirectories(withTS, endpoint.getMetadataTimeout(), Context.NONE).stream()
                    .toArray(ShareFileItem[]::new);
        } catch (RuntimeException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        }
    }

    @Override
    public boolean sendNoop() throws GenericFileOperationFailedException {
        log.trace("sendNoOp()");
        return existsRemote(root);
    }

    @Override
    public boolean sendSiteCommand(String command) throws GenericFileOperationFailedException {
        log.trace("sendSiteCommand({})", command);
        return true;
    }

    public ShareServiceClient getClient() {
        if (client == null) {
            client = createClient();
        }
        return client;
    }

    private ShareServiceClient createClient() {

        var builder = new ShareServiceClientBuilder().endpoint(HTTPS + "://" + configuration.getHost());
        var sharedKey = configuration.getSharedKey();
        if (configuration.getCredentialType().equals(CredentialType.SHARED_ACCOUNT_KEY)) {
            if (sharedKey != null) {
                var keyB64 = FilesURIStrings.reconstructBase64EncodedValue(sharedKey);
                builder.credential(new StorageSharedKeyCredential(configuration.getAccount(), keyB64));
            } else if (configuration.getCredentialType().equals(CredentialType.AZURE_SAS)) {
                builder = builder.sasToken(token.toURIQuery());
            } else if (configuration.getCredentialType().equals(CredentialType.AZURE_IDENTITY)) {
                builder = builder.credential(new DefaultAzureCredentialBuilder().build());
            }
        }
        return builder.buildClient();
    }

    void reconnectIfNecessary(Exchange exchange) throws GenericFileOperationFailedException {
        boolean reconnectRequired;
        try {
            reconnectRequired = !isConnected();
        } catch (GenericFileOperationFailedException e) {
            log.trace("Going to reconnect because of: ", e);
            reconnectRequired = true;
        }
        if (reconnectRequired) {
            log.trace("Probing if the file service is connectible ...");
            connect(configuration, exchange);
        }
    }

}
