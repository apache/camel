package org.apache.camel.component.quickfixj;

import java.util.ArrayList;
import java.util.List;

import quickfix.Field;
import quickfix.FieldMap;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;
import quickfix.field.SenderCompID;
import quickfix.field.TargetCompID;

public class MessagePredicate {
	private final List<Field<String>> headerCriteria = new ArrayList<Field<String>>();
	private final List<Field<String>> bodyCriteria = new ArrayList<Field<String>>();
	
	public MessagePredicate(SessionID requestingSessionID, String msgType) {
		// Reverse session ID for reply
		// TODO may need to optionally include subID and locationID
		addHeaderFieldIfPresent(SenderCompID.FIELD, requestingSessionID.getTargetCompID());
		addHeaderFieldIfPresent(TargetCompID.FIELD, requestingSessionID.getSenderCompID());
		withMessageType(msgType);
	}
	
	private void addHeaderFieldIfPresent(int tag, String value) {
		if (value != null && !"".equals(value)) {
			withHeaderField(tag, value);
		}
	}

	public boolean evaluate(Message message) {
		return evaluate(message, bodyCriteria) && evaluate(message.getHeader(), headerCriteria);
	}
	
	private boolean evaluate(FieldMap fieldMap, List<Field<String>> criteria) {
		for (Field<String> c : criteria) {
			String value = null;
			try {
				if (fieldMap.isSetField(c.getField())) {
					value = fieldMap.getString(c.getField());
				}
			} catch (FieldNotFound e) {
				// ignored, shouldn't happen
			}
			if (!c.getObject().equals(value)) {
				return false;
			}
		}
		return true;
	}

	public MessagePredicate withField(int tag, String value) {
		bodyCriteria.add(new Field<String>(tag, value));
		return this;
	}

	public MessagePredicate withHeaderField(int tag, String value) {
		headerCriteria.add(new Field<String>(tag, value));
		return this;
	}

	private MessagePredicate withMessageType(String msgType) {
		headerCriteria.add(new Field<String>(MsgType.FIELD, msgType));
		return this;
	}
}
