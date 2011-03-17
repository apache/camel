declare variable $in.headers.foo as xs:string external;
<transformed sender="{$in.headers.foo}" subject="{mail/subject}">
{.}
</transformed>