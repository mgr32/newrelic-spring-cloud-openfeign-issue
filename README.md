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

The root issue seems to be a too broad cache key in [WeavePackageManager#validPackages](https://github.com/newrelic/newrelic-java-agent/blob/7ee30b57089e6817a93832eb6492500579046724/newrelic-weaver/src/main/java/com/newrelic/weave/weavepackage/WeavePackageManager.java#L92), resulting in `PackageValidationResult` from one class (with weaving violations) being used for another class (without weaving violations).

Consider the following classes:
* `com.example.demo.TestNonController` - class for which [spring-4.3.0 instrumentation](https://github.com/newrelic/newrelic-java-agent/blob/7ee30b57089e6817a93832eb6492500579046724/instrumentation/spring-4.3.0/src/main/java/com/nr/agent/instrumentation/SpringController_Instrumentation.java) should not apply (as there is `@GetMapping` but there is no `@RestController` mapping)
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