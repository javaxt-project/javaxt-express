This directory contains JavaXT javascripts.



mklink /H javaxt-express.js "C:\Users\petya\Documents\JavaScript\javaxt-express\releases\javaxt-express.min.js"
mklink /H javaxt-webcontrols.js "C:\Users\petya\Documents\JavaScript\javaxt\releases\javaxt.min.js"


NOTE: mklink cannot be used to delete a symbolic link.
To remove a symbolic link, simply delete them as if you're removing a normal file.
Just make sure you don't delete the original file.


fsutil can be used to verify link
fsutil hardlink list "C:\Users\petya\Documents\JavaScript\javaxt-express\releases\javaxt-utils.min.js"
