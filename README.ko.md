# qclass-cycle-guard-plugin

QueryDSL Q-class 간 순환 의존성을 감지하고, `META-INF/cyclic-qclasses.txt`를 생성하여 런타임에 노출하는 Gradle 플러그인입니다.

## 문제

QueryDSL이 생성하는 Q-class는 다른 Q-class를 static 필드로 참조합니다. 순환 참조가 존재할 때(예: QOrder → QCustomer → QOrder), 멀티스레드 환경에서 클래스 초기화 시 JVM 레벨 데드락이 발생할 수 있으며, 이는 진단이 매우 어렵습니다.

## 해결 방법

이 플러그인은 **빌드 시점**에 다음을 수행합니다:
1. 생성된 Q-class 파일을 스캔하여 의존성 그래프를 구성
2. Tarjan의 강한 연결 요소(SCC) 알고리즘으로 사이클 감지
3. 사이클에 포함된 Q-class의 FQCN 목록을 `META-INF/cyclic-qclasses.txt`에 기록

## 사용법

### 1. 플러그인 추가

**settings.gradle**
```groovy
pluginManagement {
    repositories {
        maven { url 'https://jitpack.io' }
        gradlePluginPortal()
    }
}
```

**build.gradle**
```groovy
plugins {
    id 'com.github.jsjg73.qclass-cycle-guard' version 'v0.2.0'
}
```

### 2. 빌드

```bash
./gradlew build
```

플러그인이 자동으로 사이클을 감지하고 `META-INF/cyclic-qclasses.txt`를 빌드 출력에 포함시킵니다.

### 3. 런타임에서 리소스 활용

```java
Enumeration<URL> resources = getClass().getClassLoader()
    .getResources("META-INF/cyclic-qclasses.txt");
// FQCN을 읽어 멀티스레드 시작 전에 단일 스레드에서 로딩
```

## 동작 방식

1. **스캔** — Q-class 소스 파일을 파싱하여 `new QXxx()` 생성자 호출을 찾음
2. **감지** — 의존성 그래프를 구성하고 Tarjan SCC 알고리즘으로 사이클 탐지
3. **리소스** — `META-INF/cyclic-qclasses.txt` 작성 (FQCN 1줄씩, 알파벳 순 정렬)

## 빌드 파이프라인

```
compileJava → detectQClassCycle → copyQClassCycleGuardResource → classes
```

## 호환성

- Java 11+
- Gradle 7.x / 8.x
- QueryDSL 4.x / 5.x

## 라이선스

[Apache License 2.0](LICENSE)
