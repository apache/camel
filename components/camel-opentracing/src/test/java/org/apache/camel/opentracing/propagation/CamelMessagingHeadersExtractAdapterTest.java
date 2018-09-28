package org.apache.camel.opentracing.propagation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import static org.apache.camel.opentracing.propagation.CamelMessagingHeadersInjectAdapter.JMS_DASH;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.junit.Test;

public class CamelMessagingHeadersExtractAdapterTest {
	
	@Test
	public void noProperties() {
		final Map<String, Object> map = new HashMap<String, Object>();
		CamelMessagingHeadersExtractAdapter adapter = new CamelMessagingHeadersExtractAdapter(map);
		Iterator<Map.Entry<String, String>> iterator = adapter.iterator();
		assertFalse(iterator.hasNext());
	}

	@Test
	public void oneProperty() {
		final Map<String, Object> map = new HashMap<String, Object>();
		map.put("key", "value");
		CamelMessagingHeadersExtractAdapter adapter = new CamelMessagingHeadersExtractAdapter(map);
		Iterator<Map.Entry<String, String>> iterator = adapter.iterator();
		Map.Entry<String, String> entry = iterator.next();
		assertEquals("key", entry.getKey());
		assertEquals("value", entry.getValue());
	}

	@Test
	public void propertyWithDash() {
		final Map<String, Object> map = new HashMap<String, Object>();
		map.put(JMS_DASH + "key" + JMS_DASH + "1" + JMS_DASH, "value1");
		CamelMessagingHeadersExtractAdapter adapter = new CamelMessagingHeadersExtractAdapter(map);
		Iterator<Map.Entry<String, String>> iterator = adapter.iterator();
		Map.Entry<String, String> entry = iterator.next();
		assertEquals("-key-1-", entry.getKey());
		assertEquals("value1", entry.getValue());
	}
}
