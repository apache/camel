<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Apache Camel ${it.version}</title>
</head>
<body>

<h1>Welcome to Apache Camel ${it.version}</h1>

<p>Welcome to the Web Console for instance <b>${it.name}</b>.</p>
<p>We hope you find the following links helpful</p>

<ul>
  <li>
    <a href="<c:url value='/endpoints'/>" title="View current endpoints or create new ones">Endpoints</a>
  </li>
  <li>
    <a href="<c:url value='/routes'/>" title="View current routes">Routes</a>
  </li>
  <li>
    <a href="<c:url value='/api'/>" title="Documentation on the REST API to Camel">API</a>
  </li>
</ul>

<p>The following diagnostic links might be useful too...
</p>

<ul>
  <li>
    <a href="<c:url value='/components'/>" title="View the available components you can use with Camel">Components</a>
  </li>
  <li>
    <a href="<c:url value='/languages'/>" title="View the available languages you can use with Camel">Languages</a>
  </li>
  <li>
    <a href="<c:url value='/converters'/>" title="View the available type converters currently registered with Camel">Type Converters</a>
  </li>
  <li>
    <a href="<c:url value='/systemProperties'/>" title="View the System Properties used to create this service">System Properties</a>
  </li>
</ul>

<p>Lets take it for a ride!</p>

</body>
</html>
