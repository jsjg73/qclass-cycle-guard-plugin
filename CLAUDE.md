# qclass-cycle-guard-plugin

QueryDSL Q-class 간 순환 참조로 인한 `<clinit>` 데드락을 **빌드 타임에 감지·방지**하는 Gradle 플러그인.

## 문제

QueryDSL이 생성하는 Q-class들이 서로 static 필드로 참조할 때, 멀티스레드 환경에서 JVM class initialization lock이 교차되면 데드락이 발생한다. 이 문제는 진단이 극히 어렵다.

## 핵심 구조

| 클래스 | 역할 |
|--------|------|
| `QClassCycleGuardPlugin` | Plugin 진입점. 태스크 등록 및 의존 설정 |
| `QClassCycleGuardTask` | 파이프라인 오케스트레이션 |
| `QClassScanner` | Q-class 소스 스캔 → 의존 그래프 구축 |
| `QClassInfo` | Q-class 메타정보 VO |
| `TarjanScc` | Tarjan SCC 알고리즘으로 순환 탐지 |
| `CyclicQClassResourceWriter` | `META-INF/cyclic-qclasses.txt` 생성 |

## 빌드 파이프라인

```
compileJava → detectQClassCycle → copyQClassCycleGuardResource → classes
```

## Plugin ID

`com.github.jsjg73.qclass-cycle-guard-plugin`

## 호환성

Java 11+, Gradle 7.x/8.x, QueryDSL 4.x/5.x