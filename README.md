# JRobotX

Library to provide compliance with the Web Robot Exclusion protocol (robots.txt)

## clementdenis fork
Forked to allow parsing of Googlebot-style pattern matching rules

## kenduck fork
* Fixed a bug which was causing the test cases to fail
* Adding robots.txt caching.

## Usage

```java
    import com.trigonic.jrobotx.RobotExclusion;

    // ...
    
    RobotExclusion robotExclusion = new RobotExclusion();
    if (robotExclusion.allows(url, userAgentString)) {
        // do something with url
    }
```

    To provide a folder to use for caching robots.txt files:
    
```java
    import com.trigonic.jrobotx.RobotExclusion;

    // ...
    
    File cacheDir = ...
    
    // ...
    
    RobotExclusion robotExclusion = new RobotExclusion(cacheDir);
    if (robotExclusion.allows(url, userAgentString)) {
        // do something with url
    }
```
    