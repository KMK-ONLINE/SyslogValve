This is Marco's Syslog Valve for Apache Tomcat, fixed such that it works with tomcat7.
See http://marcoscorner.walther-family.org/2012/06/apache-tomcat-and-logging-the-2nd/ for more details.

Additional parameters from this fork:
- `port`: Syslog UDP target port
- `msgLength`: UDP packet message length to be sent to syslog
  > - Maximum: 65507
  > - Minimum: 480

### Example:  
```
<Valve className="org.apache.catalina.valves.SyslogAccessLogValve"
	requestAttributesEnabled="true"
	hostname="localhost"
	port="514"
	msgLength="32766"
	resolveHosts="false"
	pattern="%h %l %u %t &quot;%r&quot; %s %b" />
```

### How to: Maven Release ###
1. make sure all your changes are pushed to remote master
2. make sure `mvn clean install` succeeds
3. do release file/config cleanup `rm pom.xml.releaseBackup release.properties`
4. do prepare step via `mvn release:prepare`; leave inputs as default (auto-increments versions)
   - this step will create a new local tag based on the release inputs
   - this step will do 2 local commits on pom version increments for the release and development cycle
5. push the tag created by the preparation step (`step #4`) via `git push origin <tag-name>`
6. do release step via `mvn release:perform`; this will output release jar, e.g. `${project.basedir}/target/checkout/SyslogValve/SyslogValve/0.1.0/SyslogValve-0.1.0-jar-with-dependencies.jar`
7. push the 2 commits created by the preparation step (`step #4`) via `git push origin master`
8. do `step #3` as final cleanup step

~ FIN
