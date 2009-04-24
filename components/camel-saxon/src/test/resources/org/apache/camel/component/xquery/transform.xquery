declare variable $in.headers.foo as xs:string external;
<transformed subject="{mail/subject}" sender="{$in.headers.foo}">
{.}
</transformed>