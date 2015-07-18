package org.apache.camel.component.git;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitProducer extends DefaultProducer{

    private static final Logger LOG = LoggerFactory.getLogger(GitProducer.class);
    private final GitEndpoint endpoint;
    
	public GitProducer(GitEndpoint endpoint) {
		super(endpoint);
		this.endpoint = endpoint;
	}

	@Override
	public void process(Exchange exchange) throws Exception {
        String operation;		
	    if (ObjectHelper.isEmpty(endpoint.getOperation())) {
	        operation = exchange.getIn().getHeader(GitConstants.GIT_OPERATION, String.class);
	    } else {
	    	operation = endpoint.getOperation();
	    }
	    
	    switch (operation) {
	    case GitOperation.CLONE_OPERATION:
	    	doClone(exchange, operation);
	    	break;
	    	
	    case GitOperation.INIT_OPERATION:
	    	doInit(exchange, operation);
	    	break;

	    case GitOperation.ADD_OPERATION:
	    	doAdd(exchange, operation);
	    	break;	    	
	    }
	}
	
    protected void doClone(Exchange exchange, String operation) {
    	Git result = null;
    	if (ObjectHelper.isEmpty(endpoint.getLocalPath())) {
    		throw new IllegalArgumentException("Local path must specified to execute " + operation);
    	}
    	try {
    		File localRepo = new File(endpoint.getLocalPath(), "");
    		if (!localRepo.exists()) {
			   result = Git.cloneRepository().setURI(endpoint.getRemotePath()).setDirectory(new File(endpoint.getLocalPath(),"")).call();
    		} else {
               throw new IllegalArgumentException("The local repository directory already exists");
    		}
		} catch (Exception e) {
			LOG.error("There was an error in Git " + operation + " operation");
			e.printStackTrace();
		} finally {
			result.close();
		}
    }

    protected void doInit(Exchange exchange, String operation) {
    	Git result = null;
    	if (ObjectHelper.isEmpty(endpoint.getLocalPath())) {
    		throw new IllegalArgumentException("Local path must specified to execute " + operation);
    	}
    	try {
			result = Git.init().setDirectory(new File(endpoint.getLocalPath(),"")).call();
		} catch (Exception e) {
			LOG.error("There was an error in Git " + operation + " operation");
			e.printStackTrace();
		} finally {
			result.close();
		}
    }
    
    protected void doAdd(Exchange exchange, String operation) {
    	String fileName = null;
    	if (ObjectHelper.isEmpty(endpoint.getLocalPath())) {
    		throw new IllegalArgumentException("Local path must specified to execute " + operation);
    	}
    	if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_FILE_NAME))) {
    		fileName = exchange.getIn().getHeader(GitConstants.GIT_FILE_NAME, String.class);
    	} else {
    		throw new IllegalArgumentException("File name must be specified to execute " + operation);
    	}
    	try {
			Git.open(new File(endpoint.getLocalPath())).add().addFilepattern(fileName).call();
		} catch (Exception e) {
			LOG.error("There was an error in Git " + operation + " operation");
			e.printStackTrace();
		}
    }
}
