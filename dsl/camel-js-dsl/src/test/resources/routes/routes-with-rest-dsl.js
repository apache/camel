
rest('/')
    .produces("text/plain")
    .get('/say/hello')
    .route()
        .transform().constant("Hello World");

