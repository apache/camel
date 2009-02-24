<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Endpoint ${it.uri}</title>
</head>
<body>

<h1>Endpoint: ${it.uri}</h1>

<form action='<c:url value="${it.href}"/>' method="post" name="sendMessage">
  <input type="submit" value="Send"> <input type="reset"> <br>
  <textarea name="body" rows="30" cols="80"></textarea>
</form>

</body>
</html>
