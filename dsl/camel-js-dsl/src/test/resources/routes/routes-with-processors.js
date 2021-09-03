const Processor = Java.type("org.apache.camel.Processor");
const p = Java.extend(Processor);
const f = new p(function(e) { e.getMessage().setBody('function') });
const a = new p(e => { e.getMessage().setBody('arrow') });
const w = processor(e => { e.getMessage().setBody('wrapper') });

from('direct:function')
    .process(f);

from('direct:arrow')
    .process(a);

from('direct:wrapper')
    .process(w);

