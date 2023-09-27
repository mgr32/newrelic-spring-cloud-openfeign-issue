### Overview

This is a simple project showing the inconsistency in transaction names given by `newrelic-java-agent`.

### Steps to reproduce

1. Configure New Relic Java Agent 8.6.0 as per instructions at https://docs.newrelic.com/install/java/ (`newrelic.yml` used
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

| Spring Boot | Request | Controller  | Response Code | Transaction name at one.newrelic.com  | Expected? |
|-------------|-------------------------------|----------------|---------------|---------------------------------------------------------|---------|
| 2.7.16      | /hello  | `@RestController` / unsecured | 200           | WebTransaction/SpringController/hello (GET) | Y |
| 3.1.4       | /hello  | `@RestController` / unsecured | 200           | WebTransaction/SpringController/hello (GET) | Y |
| 2.7.16      | /hello-secured  | `@RestController` / secured / valid auth | 200           | WebTransaction/SpringController/hello-secured (GET) | Y |
| 3.1.4       | /hello-secured  | `@RestController` / secured / valid auth | 200           | WebTransaction/SpringController/hello-secured (GET) | Y |
| 2.7.16      | /hello-secured  | `@RestController` / secured / invalid auth | 401           | WebTransaction/SpringController/BasicErrorController/error | Y |
| 3.1.4       | /hello-secured  | `@RestController` / secured / invalid auth | 401           | **WebTransaction/Servlet/dispatcherServlet** | **N** |
| 2.7.16      | /hello-custom  | custom annotation | 200           | WebTransaction/SpringController/TestControllerWithCustomAnnotation/sayHello | Y |
| 3.1.4       | /hello-custom  | custom annotation | 200           | **WebTransaction/Servlet/dispatcherServlet** | **N** |
| 2.7.16      | /actuator/health  | registered automatically without annotation | 200           | WebTransaction/SpringController/OperationHandler/handle | Y |
| 3.1.4       | /actuator/health  | registered automatically without annotation | 200           | **WebTransaction/Servlet/dispatcherServlet** | **N** |
| 2.7.16      | /not-existing  | no handler mapping | 404           | WebTransaction/SpringController/BasicErrorController/error | Y |
| 3.1.4       | /not-existing  | no handler mapping | 404           | **WebTransaction/Servlet/dispatcherServlet** | **N** |

`/hello-custom` is configured in the following way:
```
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@RestController
public @interface CustomRestControllerAnnotation {
}
```

```
@CustomRestControllerAnnotation
public class TestControllerWithCustomAnnotation {
    @GetMapping("/hello-custom")
    public String sayHello() {
        return "hello-custom";
    }
}
```

`/actuator-health` is configured automatically due to the presence of `spring-boot-starter-actuator`.