# qclass-cycle-guard-plugin

A Gradle plugin that detects cyclic dependencies between QueryDSL Q-classes and generates `META-INF/cyclic-qclasses.txt` to expose them at runtime.

## Problem

QueryDSL generates Q-classes with static fields referencing other Q-classes. When circular references exist (e.g., QOrder → QCustomer → QOrder), multi-threaded class initialization can cause JVM-level deadlocks that are extremely hard to diagnose.

## Solution

This plugin runs at **build time** to:
1. Scan generated Q-class files and build a dependency graph
2. Detect cycles using Tarjan's Strongly Connected Components algorithm
3. Write `META-INF/cyclic-qclasses.txt` listing all cyclic Q-class FQCNs for runtime discovery

## Quick Start

### 1. Add plugin

**settings.gradle**
```groovy
pluginManagement {
    repositories {
        maven { url 'https://jitpack.io' }
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == 'com.github.jsjg73.qclass-cycle-guard-plugin') {
                useModule("com.github.jsjg73:qclass-cycle-guard-plugin:${requested.version}")
            }
        }
    }
}
```

**build.gradle**
```groovy
plugins {
    id 'com.github.jsjg73.qclass-cycle-guard-plugin' version 'v0.3.0'
}
```

### 2. Build

```bash
./gradlew build
```

The plugin automatically detects cycles and writes `META-INF/cyclic-qclasses.txt` into the build output.

### 3. Use the resource at runtime

```java
Enumeration<URL> resources = getClass().getClassLoader()
    .getResources("META-INF/cyclic-qclasses.txt");
// read FQCNs and load them on a single thread before multi-threaded startup
```

## How It Works

1. **Scan** — Parses Q-class source files to find `new QXxx()` constructor calls
2. **Detect** — Builds a dependency graph and finds cycles via Tarjan's SCC algorithm
3. **Resource** — Writes `META-INF/cyclic-qclasses.txt` (one FQCN per line, alphabetically sorted)

## Build Pipeline

```
compileJava → detectQClassCycle → copyQClassCycleGuardResource → classes
```

## Compatibility

- Java 11+
- Gradle 7.x / 8.x
- QueryDSL 4.x / 5.x

## License

[Apache License 2.0](LICENSE)
