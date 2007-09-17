/**
 *        Copyright (c) 1993-2006 IONA Technologies PLC.
 *                       All Rights Reserved.
 */
package org.apache.camel.component.cxf.interceptors;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class RawMessageContentRedirectInterceptor extends AbstractPhaseInterceptor<Message> {
    public RawMessageContentRedirectInterceptor() {
        super(Phase.WRITE);        
    }
    
    public void handleMessage(Message message) throws Fault {

        InputStream is = message.getContent(InputStream.class);
        OutputStream os = message.getContent(OutputStream.class);
        
        try {
            System.out.println("the input stream is " + is);
            IOUtils.copy(is, os);
            is.close();
            os.flush();
        } catch (Exception e) {
            throw new Fault(e);
        }
    }
}
