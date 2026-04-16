package io.github.jsjg73.qclasscycleguard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InitializerCodeGenerator} 테스트.
 *
 * <p>순환이 감지된 Q-class 정보를 주면,
 * 앱 시작 시 싱글스레드로 로딩하는 Java 소스를 올바르게 생성하는지 검증한다.</p>
 */
class InitializerCodeGeneratorTest {

    private static final String CONFIG_PKG = "com.example.config";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("순환에 포함된 Q-class의 FQCN 목록을 수집한다")
    void collectsCyclicFqcns() {
        // given: QA ↔ QB 순환
        Map<String, QClassInfo> infoMap = Map.of(
            "QA", new QClassInfo("QA", "com.example.entity"),
            "QB", new QClassInfo("QB", "com.example.entity")
        );
        List<Set<String>> cycles = List.of(Set.of("QA", "QB"));

        // when
        InitializerCodeGenerator generator = new InitializerCodeGenerator(cycles, infoMap, CONFIG_PKG);
        List<String> fqcns = generator.collectCyclicFqcns();

        // then: FQCN이 정렬되어 반환됨
        assertThat(fqcns).containsExactly(
            "com.example.entity.QA",
            "com.example.entity.QB"
        );
    }

    @Test
    @DisplayName("QClassInitializer.java 파일이 지정된 패키지 경로에 생성된다")
    void generatesFileAtCorrectPath() throws IOException {
        // given
        Map<String, QClassInfo> infoMap = Map.of(
            "QA", new QClassInfo("QA", "com.example.entity"),
            "QB", new QClassInfo("QB", "com.example.entity")
        );
        List<Set<String>> cycles = List.of(Set.of("QA", "QB"));

        // when
        InitializerCodeGenerator generator = new InitializerCodeGenerator(cycles, infoMap, CONFIG_PKG);
        Path generated = generator.generate(tempDir);

        // then: 지정한 패키지 경로에 생성
        assertThat(generated).exists();
        assertThat(generated.toString()).endsWith("QClassInitializer.java");
        assertThat(generated.getParent().toString())
            .endsWith("com/example/config");
    }

    @Test
    @DisplayName("생성된 코드에 Class.forName이 포함된다")
    void generatedCodeContainsRequiredElements() throws IOException {
        // given
        Map<String, QClassInfo> infoMap = Map.of(
            "QCmsBaseEntity", new QClassInfo("QCmsBaseEntity", "com.example.entity.cms"),
            "QCmsManager", new QClassInfo("QCmsManager", "com.example.entity.cms")
        );
        List<Set<String>> cycles = List.of(Set.of("QCmsBaseEntity", "QCmsManager"));

        // when
        InitializerCodeGenerator generator = new InitializerCodeGenerator(cycles, infoMap, CONFIG_PKG);
        Path generated = generator.generate(tempDir);
        String code = Files.readString(generated);

        // then
        assertThat(code).contains("package " + CONFIG_PKG + ";");
        assertThat(code).contains("public static void init()");
        assertThat(code).contains("Class.forName(className)");
        assertThat(code).contains("com.example.entity.cms.QCmsBaseEntity");
        assertThat(code).contains("com.example.entity.cms.QCmsManager");
    }

    @Test
    @DisplayName("생성된 코드에 순환 관계가 주석으로 기록된다")
    void generatedCodeDocumentsCycles() throws IOException {
        // given
        Map<String, QClassInfo> infoMap = Map.of(
            "QA", new QClassInfo("QA", "com.example"),
            "QB", new QClassInfo("QB", "com.example")
        );
        List<Set<String>> cycles = List.of(Set.of("QA", "QB"));

        // when
        InitializerCodeGenerator generator = new InitializerCodeGenerator(cycles, infoMap, CONFIG_PKG);
        Path generated = generator.generate(tempDir);
        String code = Files.readString(generated);

        // then: Javadoc에 순환 관계 기록
        assertThat(code).contains("감지된 순환 참조:");
        assertThat(code).contains("<->");
    }

    @Test
    @DisplayName("순환이 없으면 빈 init() 메서드를 가진 no-op 클래스가 생성된다")
    void generatesNoOpWhenNoCycles() throws IOException {
        // given: 순환 없음 (CI 환경 등에서 스캔 결과가 없는 경우)
        List<Set<String>> noCycles = List.of();
        Map<String, QClassInfo> emptyInfo = Map.of();

        // when
        InitializerCodeGenerator generator = new InitializerCodeGenerator(noCycles, emptyInfo, CONFIG_PKG);
        Path generated = generator.generate(tempDir);
        String code = Files.readString(generated);

        // then: 컴파일 가능한 유효한 Java 코드이며 init() 메서드가 존재
        assertThat(generated).exists();
        assertThat(code).contains("public static void init()");
        assertThat(code).contains("package " + CONFIG_PKG + ";");
        // CYCLIC_Q_CLASSES 배열이 비어있음 (Class.forName 대상 없음)
        assertThat(code).contains("private static final String[] CYCLIC_Q_CLASSES = {\n\n    };");
    }
}
