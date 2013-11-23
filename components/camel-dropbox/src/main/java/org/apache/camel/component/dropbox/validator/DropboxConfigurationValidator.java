package org.apache.camel.component.dropbox.validator;

import org.apache.camel.component.dropbox.DropboxConfiguration;
import org.apache.camel.component.dropbox.producer.*;
import org.apache.camel.component.dropbox.util.DropboxException;
import org.apache.camel.component.dropbox.util.DropboxOperation;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: hifly
 * Date: 11/23/13
 * Time: 3:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class DropboxConfigurationValidator {

    public static void validate(DropboxConfiguration configuration) throws DropboxException{
        validateCommonProperties(configuration);
        DropboxOperation op = configuration.getOperation();
        if(op == DropboxOperation.get) {
            validateGetOp(configuration);
        }
        else if(op == DropboxOperation.put) {
            validatePutOp(configuration);
        }
        else if(op == DropboxOperation.search) {
            validateSearchOp(configuration);
        }
        else if(op == DropboxOperation.del) {
            validateDelOp(configuration);
        }
        else if(op == DropboxOperation.move) {
            validateMoveOp(configuration);
        }
    }

    private static void validateCommonProperties(DropboxConfiguration configuration) throws DropboxException {
        if(configuration.getAccessToken()==null || configuration.getAccessToken().equals("")) {
            throw new DropboxException("option <access token> is not present or not valid!");
        }
        if(configuration.getAppKey()==null || configuration.getAppKey().equals("")) {
            throw new DropboxException("option <app token> is not present or not valid!");
        }
        if(configuration.getAppSecret()==null || configuration.getAppSecret().equals("")) {
            throw new DropboxException("option <app secret> is not present or not valid!");
        }
        DropboxOperation op = configuration.getOperation();
        if(op != DropboxOperation.put || op != DropboxOperation.search || op != DropboxOperation.del
                || op != DropboxOperation.get || op != DropboxOperation.move) {
            throw new DropboxException("operation specified is not valid!");
        }
    }

    private static void validateGetOp(DropboxConfiguration configuration) throws DropboxException {
        if(configuration.getRemotePath()==null || configuration.getRemotePath().equals("")) {
            throw new DropboxException("option <remote path> is not present or not valid!");
        }
    }

    private static void validatePutOp(DropboxConfiguration configuration) throws DropboxException {
        if(configuration.getLocalPath()==null || configuration.getLocalPath().equals("")) {
            throw new DropboxException("option <local path> is not present or not valid!");
        }
    }

    private static void validateSearchOp(DropboxConfiguration configuration) throws DropboxException {
        if(configuration.getRemotePath()==null || configuration.getRemotePath().equals("")) {
            throw new DropboxException("option <remote path> is not present or not valid!");
        }
    }

    private static void validateDelOp(DropboxConfiguration configuration) throws DropboxException {
        if(configuration.getRemotePath()==null || configuration.getRemotePath().equals("")) {
            throw new DropboxException("option <remote path> is not present or not valid!");
        }
    }

    private static void validateMoveOp(DropboxConfiguration configuration) throws DropboxException {
        if(configuration.getRemotePath()==null || configuration.getRemotePath().equals("")) {
            throw new DropboxException("option <remote path> is not present or not valid!");
        }
        if(configuration.getNewRemotePath()==null || configuration.getNewRemotePath().equals("")) {
            throw new DropboxException("option <new remote path> is not present or not valid!");
        }
    }
}
