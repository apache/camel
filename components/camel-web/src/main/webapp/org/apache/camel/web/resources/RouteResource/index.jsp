<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Route ${it.route.id}</title>
  <link href='<c:url value="/css/prettify/prettify.css"/>' type="text/css" rel="stylesheet" />
  <script type="text/javascript" src='<c:url value="/js/prettify/prettify.js"/>'></script>
</head>

<body onload="prettyPrint()">

<h2>Route  ${it.route.id}</h2>

<p>${it.route.description.text}</p>

<div class="route">
<pre class="prettyprint"><c:out value="${it.routeXml}" escapeXml="true" />  
</pre>
</div>

<ul>
<li><a href='<c:url value="/routes/${it.route.id}/lang/xml/edit"/>'>Edit Route in Xml</a>
<li><a href='<c:url value="/routes/${it.route.id}/lang/groovy/edit"/>'>Edit Route in Groovy</a>
<li>Edit Route in Ruby
<li>Edit Route in Scala 
</ul>


</body>
</html>
