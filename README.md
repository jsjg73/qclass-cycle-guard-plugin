# qclass-cycle-guard-plugin

A Gradle plugin that detects cyclic dependencies between QueryDSL Q-classes and generates pre-initialization code to prevent `<clinit>` deadlocks.

## Problem

QueryDSL generates Q-classes with static fields referencing other Q-classes. When circular references exist (e.g., QOrder → QCustomer → QOrder), multi-threaded class initialization can cause JVM-level deadlocks that are extremely hard to diagnose.

## Solution

This plugin runs at **build time** to:
1. Scan generated Q-class files and build a dependency graph
2. Detect cycles using Tarjan's Strongly Connected Components algorithm
3. Generate a `QClassInitializer` class that pre-loads cyclic Q-classes on the main thread

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
            if (requested.id.id == 'io.github.jsjg73.qclass-cycle-guard') {
                useModule("com.github.jsjg73:qclass-cycle-guard-plugin:${requested.version}")
            }
        }
    }
}
```

**build.gradle**
```groovy
plugins {
    id 'io.github.jsjg73.qclass-cycle-guard' version 'v0.1.0'
}

qclassCycleGuard {
    configPackage = 'com.example.config'
}
```

### 2. Build

```bash
./gradlew build
```

The plugin automatically detects cycles and generates `QClassInitializer.java` in the specified package.

### 3. Use in application

```java
public static void main(String[] args) {
    QClassInitializer.init();  // Pre-load cyclic Q-classes
    SpringApplication.run(Application.class, args);
}
```

## Configuration

```groovy
qclassCycleGuard {
    configPackage = 'com.example.config'  // Package for generated QClassInitializer
}
```

## How It Works

1. **Scan** — Parses Q-class source files to find `new QXxx()` constructor calls
2. **Detect** — Builds a dependency graph and finds cycles via Tarjan's SCC algorithm
3. **Generate** — Creates `QClassInitializer.java` with `Class.forName()` calls for all cyclic Q-classes
4. **Resource** — Writes `META-INF/cyclic-qclasses.txt` for runtime discovery

## Compatibility

- Java 11+
- Gradle 7.x / 8.x
- QueryDSL 4.x / 5.x

## License

[Apache License 2.0](LICENSE)
