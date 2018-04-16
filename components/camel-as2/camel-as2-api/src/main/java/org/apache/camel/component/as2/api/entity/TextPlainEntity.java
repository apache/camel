package org.apache.camel.component.as2.api.entity;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.CanonicalOutputStream;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.entity.ContentType;
import org.apache.http.util.Args;

public class TextPlainEntity extends MimeEntity {
    
    private String content;
    
    public TextPlainEntity(String content, String charset, String contentTransferEncoding, boolean isMainBody) {
        this.content = Args.notNull(content, "Content");
        setContentType(ContentType.create(AS2MediaType.TEXT_PLAIN, charset));
        setContentTransferEncoding(contentTransferEncoding);
        setMainBody(isMainBody);
    }
    
    public String getText() {
        return content;
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        NoCloseOutputStream ncos = new NoCloseOutputStream(outstream);
        try (CanonicalOutputStream canonicalOutstream = new CanonicalOutputStream(ncos, AS2Charset.US_ASCII)) {

            // Write out mime part headers if this is not the main body of message.
            if (!isMainBody()) { 
                HeaderIterator it = headerIterator();
                while (it.hasNext()) {
                    Header header = it.nextHeader();
                    canonicalOutstream.writeln(header.toString());
                }
                canonicalOutstream.writeln(); // ensure empty line between headers and body; RFC2046 - 5.1.1
            }
            
            // Write out content
            canonicalOutstream.write(content.getBytes(AS2Charset.US_ASCII), 0, content.length());
        }            
    }


}
