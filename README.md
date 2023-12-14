### Overview

This is a simple project showing the inconsistency in transaction names given by `newrelic-java-agent`.

### Steps to reproduce

1. Configure New Relic Java Agent 8.7.0 as per instructions at https://docs.newrelic.com/install/java/ (`newrelic.yml` used
   for tests can be found in `src/main/resources` - it has not been modified apart from `app_name`, `license_key`
   and `log_level`).
2. Run `./gradlew clean bootJar`
3. Run `java -javaagent:/full/path/to/newrelic.jar -jar build/libs/demo-0.0.1-SNAPSHOT.jar` (JDK used in tests: OpenJDK Temurin-17.0.6+10 x64)
4. Access an endpoint at `http://localhost:8080/hello-custom` - e.g. `curl http://localhost:8080/hello-custom` (the list of problematic endpoints can be found below).
5. Check the transaction name of the request at `https://one.newrelic.com/data-exploration`,
   e.g. `SELECT appName, http.statusCode, name, transactionSubType, transactionType FROM Transaction WHERE transactionType = 'Web' AND appName = 'myapptest'`:
   * it will be `WebTransaction/Servlet/dispatcherServlet` (**unexpected**).
6. Switch to branch `springpointcut-sb2` and run it again. Now the transaction name will be `WebTransaction/SpringController/TestControllerWithCustomAnnotation/sayHello`.

### Requests details

* `curl http://localhost:8080/hello`
* `curl http://localhost:8080/hello-secured --header 'Authorization: Basic dXNlcjpwYXNzd29yZA=='`
* `curl http://localhost:8080/hello-secured`
* `curl http://localhost:8080/hello-custom`
* `curl http://localhost:8080/actuator/health`
* `curl http://localhost:8080/not-existing`
* `curl http://localhost:8080/hello-with-manual-instrumentation` (custom instrumentation set up in New Relic UI)
* `curl http://localhost:8080/hello-error`
* `curl http://localhost:8080/hello-new/with-interface`
* `curl http://localhost:8080/hello-new/with-inheritance`

The results can be found in the description and comments of https://github.com/newrelic/newrelic-java-agent/issues/1523.