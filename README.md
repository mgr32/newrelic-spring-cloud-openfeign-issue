### Overview

This is a simple project showing the inconsistency in transaction names given by `newrelic-java-agent`, depending on the order of class loading (making it non-deterministic between different applications and innocuous classpath changes in the same application).

### Steps to reproduce

1. Configure New Relic Java Agent 8.2.0 as per instructions at https://docs.newrelic.com/install/java/ (`newrelic.yml` used
   for tests can be found in `src/main/resources` - it has not been modified apart from `app_name`, `license_key`
   and `log_level`).
2. Run `./gradlew clean bootJar`
3. Run `java -javaagent:/full/path/to/newrelic.jar -jar build/libs/demo-0.0.1-SNAPSHOT.jar` (JDK used in tests: OpenJDK Temurin-17.0.6+10 x64)
4. Access the endpoint at `http://localhost:8080/hello` - e.g. `curl http://localhost:8080/hello`.
5. Check the transaction name of the request at `https://one.newrelic.com/data-exploration`,
   e.g. `SELECT appName, http.statusCode, name, transactionSubType, transactionType FROM Transaction WHERE transactionType = 'Web' AND appName = 'myapptest'`:
    * it will be `WebTransaction/Servlet/dispatcherServlet` (**unexpected** - this is because `ClassLoadingOrderEnforcer` loads `TestNonController` class first, and `TestController` last).

6. Change the order of class loading by commenting out the `not ok` section and uncommenting `ok` (ensuring that `TestController` class is loaded first and `TestNonController` last) and run steps 2-5 again:
    * it will be `WebTransaction/SpringController/hello (GET)` (**expected**)

### Results screenshot

The following screenshot presents the results at `one.newrelic.com` (both entries are from the same application and the same `GET /hello` request, but with the different order of class loading). 

![NewRelic events](newrelic-events.png)

### New Relic Agent log entries

The following entries were observed in `newrelic_agent.log`:

* When the generated transaction name is `WebTransaction/SpringController/hello (GET)`:

```
com.newrelic FINE: com.newrelic.instrumentation.spring-4.3.0: weaved target jdk.internal.loader.ClassLoaders$AppClassLoader@251a69d7-com/example/demo/TestController
com.newrelic FINE: 	com/nr/agent/instrumentation/SpringController_Instrumentation.sayHello:()Ljava/lang/String;
com.newrelic FINE: 	com/nr/agent/instrumentation/SpringController_Instrumentation.sayHello:()Ljava/lang/String;
```

* When the generated transaction name is `WebTransaction/Servlet/dispatcherServlet` (in this case there are no lines
  containing `TestController`):

```
com.newrelic FINEST: Skipping instrumentation module com.newrelic.instrumentation.spring-4.3.0. The most likely cause is that com.newrelic.instrumentation.spring-4.3.0 shouldn't apply to this application.
```


### Analysis

There seem to be 2 separate issues:
1) [spring-4.3.0 instrumentation](https://github.com/newrelic/newrelic-java-agent/blob/7ee30b57089e6817a93832eb6492500579046724/instrumentation/spring-4.3.0/src/main/java/com/nr/agent/instrumentation/SpringController_Instrumentation.java) not being applied for Spring controllers, depending on the order of class loading. It occurs both in Spring Boot 2 and Spring Boot 3 applications, but Spring Boot 2 weaving has a fallback - `SpringPointCut`, which does not work in Spring Boot 3 (see below).

2. [SpringPointCut](https://github.com/newrelic/newrelic-java-agent/blob/7ee30b57089e6817a93832eb6492500579046724/newrelic-agent/src/main/java/com/newrelic/agent/instrumentation/pointcuts/frameworks/spring/SpringPointCut.java#L53) expecting [HandlerAdapter#handle](https://github.com/spring-projects/spring-framework/blob/v5.3.27/spring-webmvc/src/main/java/org/springframework/web/servlet/HandlerAdapter.java#L78) method accepting objects of types `javax.servlet.http.*`. It does not work in `spring-web` >= 6.0.0 (coming with Spring Boot 3), as its [HandlerAdapter#handle](https://github.com/spring-projects/spring-framework/blob/v6.0.9/spring-webmvc/src/main/java/org/springframework/web/servlet/HandlerAdapter.java#L78) accepts objects of types `jakarta.servlet.http.*`.

Note that the transactions are named in the following way:
* if `spring-4.3.0` instrumentation is applied the transaction name will be `WebTransaction/SpringController/hello (GET)` (`spring-4.3.0` will be applied in a non-deterministic way both in Spring Boot 2 and Spring Boot 3)
* if `spring-4.3.0` instrumentation is not applied, but `SpringPointCut` instrumentation is applied, the transaction name will be `WebTransaction/SpringController/TestController/sayHello` (`SpringPointCut` will be applied only in Spring Boot 2)
* if no above instrumentations are applied, the transaction name will be `WebTransaction/Servlet/dispatcherServlet`.

The fix for the second issue (`SpringPointCut`) seems to be straightforward (migrate from `javax` to `jakarta`). Regarding the first issue (`spring-4.3.0`), see the next section.

#### Further analysis of spring-4.3.0 issue

The root cause of point 1. (`spring-4.3.0` issue) seems to be a too broad cache key in [WeavePackageManager#validPackages](https://github.com/newrelic/newrelic-java-agent/blob/7ee30b57089e6817a93832eb6492500579046724/newrelic-weaver/src/main/java/com/newrelic/weave/weavepackage/WeavePackageManager.java#L92), resulting in `PackageValidationResult` from one class (with weaving violations) being used for another class (without weaving violations).

Consider the following classes:
* `com.example.demo.TestNonController` - class for which spring-4.3.0 should not apply (as there is `@GetMapping` but there is no `@RestController` mapping)
* `com.example.demo.TestController` - class for which spring-4.3.0 instrumentation should apply

The following happens when `TestNonController` class is loaded before `TestController` class:
```
ClassLoader#loadClass("com.example.demo.TestNonController")
|- ClassWeaverService#transform
|--- WeavePackageManager#weave
|----- WeavePackageManager#match
|------- WeavePackageManager#validateAgainstClassLoader
|--------- ! hasValidated returns false (because validPackages does not contain entry for spring-4.3.0 weave package)
|--------- WeavePackage#validate -> returns PackageValidationResult with 0 violations
|--------- store PackageValidationResult in validPackages 
|------- returns PackageValidationResult with 0 violations
|----- PackageValidationResult#weave (for class name "com.example.demo.TestNonController")
|------- PackageValidationResult#getAnnotationMatchComposite
|--------- PackageValidationResult#buildResults
|----------- ClassMatch#match -> returns violation CLASS_MISSING_REQUIRED_ANNOTATIONS (correctly, as this class does not have `@RestController` and `@Controller` annotations)
|----------- !!! add the violation to violations collection
|--------- don't weave the class because violations collection is not empty (correctly)

ClassLoader#loadClass("com.example.demo.TestController")
|- ClassWeaverService#transform
|--- WeavePackageManager#weave
|----- WeavePackageManager#match   
|------- WeavePackageManager#validateAgainstClassLoader
|--------- !!! hasValidated returns true (because validPackages contains PackageValidationResult with the violation added during loading "com.example.demo.TestNonController" class)
|------- returns PackageValidationResult with 1 violation (from TestNonController class)
|----- PackageValidationResult#weave (for class name "com.example.demo.TestController")
|------- PackageValidationResult#getAnnotationMatchComposite
|--------- PackageValidationResult#buildResults
|----------- ClassMatch#match -> returns no violation (correctly, as this class has `@RestController` annotation)
|----------- !!! adds no violation to the collection, but the collection already contains 1 violation (from TestNonController class)
|--------- !!! don't weave the class because violations collection is not empty (incorrectly)
```

Thus, if `TestNonController` is loaded first, no controllers will be woven afterwards, as the violations from `TestNonController` will be cached in `WeavePackageManager#validPackages` and reused for next classes.