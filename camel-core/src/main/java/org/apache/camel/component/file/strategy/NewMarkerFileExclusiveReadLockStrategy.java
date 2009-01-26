package org.apache.camel.component.file.strategy;

import java.io.File;

import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.GenericFile;

/**
 *
 */
public class NewMarkerFileExclusiveReadLockStrategy implements GenericFileExclusiveReadLockStrategy<File> {

    public boolean acquireExclusiveReadLock(GenericFileOperations<File> fileGenericFileOperations, GenericFile<File> fileGenericFile) {
        // create the .camelFile
        return false;
    }

    public void releaseExclusiveReadLock(GenericFileOperations<File> fileGenericFileOperations, GenericFile<File> fileGenericFile) {
        // delete the .camelFile
    }

    public void setTimeout(long timeout) {
        // noop
    }

}
