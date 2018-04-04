package org.apache.camel.component.as2.api.entity;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.as2.api.util.AS2HeaderUtils;
import org.apache.camel.component.as2.api.util.AS2HeaderUtils.Parameter;
import org.apache.http.ParseException;
import org.apache.http.message.ParserCursor;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

public class DispositionNotificationOptionsParser {

    public final static DispositionNotificationOptionsParser INSTANCE = new DispositionNotificationOptionsParser();

    private final static String SIGNED_RECEIPT_PROTOCOL_ATTR_NAME = "signed-receipt-protocol";
    private final static String SIGNED_RECEIPT_MICALG_ATTR_NAME = "signed-receipt-micalg";

    public static DispositionNotificationOptions parseDispositionNotificationOptions(final String value,
                                                                                     DispositionNotificationOptionsParser parser)
            throws ParseException {
        if (value == null) {
            return new DispositionNotificationOptions(null, null);
        }

        final CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        final ParserCursor cursor = new ParserCursor(0, value.length());
        return (parser != null ? parser : DispositionNotificationOptionsParser.INSTANCE)
                .parseDispositionNotificationOptions(buffer, cursor);
    }

    public DispositionNotificationOptions parseDispositionNotificationOptions(final CharArrayBuffer buffer,
                                                                              final ParserCursor cursor) {
        Args.notNull(buffer, "buffer");
        Args.notNull(cursor, "cursor");
        
        Map<String,Parameter> parameters = new HashMap<String,Parameter>();
        while(!cursor.atEnd()) {
            Parameter parameter = AS2HeaderUtils.parseParameter(buffer, cursor);
            parameters.put(parameter.getAttribute(), parameter);
        }
        
        Parameter signedReceiptProtocolParameter = parameters.get(SIGNED_RECEIPT_PROTOCOL_ATTR_NAME);
        
        Parameter signedReceiptMicalgParameter = parameters.get(SIGNED_RECEIPT_MICALG_ATTR_NAME);
        
        
        return new DispositionNotificationOptions(signedReceiptProtocolParameter, signedReceiptMicalgParameter);
    }

}
