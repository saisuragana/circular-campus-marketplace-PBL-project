Put the JDBC driver in this folder before compiling
===================================================

1. mysql-connector-j-8.0.33.jar      (REQUIRED - you must download this once)
   https://dev.mysql.com/downloads/connector/j/
   Choose "Platform Independent", download the ZIP, and copy
   mysql-connector-j-8.0.33.jar out of it into this lib folder.
   deploy.bat then copies it into WEB-INF/lib for you.

2. servlet-api.jar                   (OPTIONAL - normally do nothing)
   compile.bat first looks for it at:
       C:\xampp\tomcat\lib\servlet-api.jar
   That copy exists in every XAMPP install. Only if it is missing,
   copy it here.

   IMPORTANT: servlet-api.jar is never copied into WEB-INF/lib.
   Tomcat supplies it at runtime and a second copy causes
   "class not found" and "illegal access" errors.

jsp-api.jar is NOT needed: this project has no JSP files, only
servlets and static HTML.
