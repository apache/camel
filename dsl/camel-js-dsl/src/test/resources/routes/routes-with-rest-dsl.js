
rest('/')
    .produces("text/plain")
    .get('/say/hello')
    .to('direct:hello');

from('direct:hello')
        .transform().constant("Hello World");

