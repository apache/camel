
const SedaType = Java.type("org.apache.camel.component.seda.SedaComponent");

const s = context.getComponent('seda', SedaType)
s.setQueueSize(1234)