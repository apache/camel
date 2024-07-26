package org.apache.camel.component.jsonata;

import com.dashjoin.jsonata.Jsonata;

public interface JsonataFrameBinding {
  void bindToFrame(Jsonata.Frame clientBuilder);
}
