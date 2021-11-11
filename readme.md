# 2FA

There is a "god" user that you can authenticate login with without any permissions.
But you will need a god scope header to do so.

Once you try to login with your credential, you will receive a poll url where you can check
if you have been granted the permission to login. This works by generating a token based on the
private keys of both users and a permissionId. This permission can be granted by your mate by
attempting to generate the same token. When a permission is granted you will receive an auth header that lasts one hour.

You can query pending permissions you have can grant, (although I think this would be nicer if it was based
on a push rather than a pull).

Once logged in you can create secrets and share them with a specific user. You can also view all secrets that 
you created or are shared with you.
