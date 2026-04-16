package io.github.jsjg73.qclasscycleguard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QClassScanner} 테스트.
 *
 * <p>실제 Q-class 소스 파일과 동일한 구조의 임시 파일을 만들어서
 * 스캐너가 의존 관계를 올바르게 파악하는지 검증한다.</p>
 */
class QClassScannerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Q-class 파일에서 패키지명과 클래스명을 추출한다")
    void extractsClassInfo() throws IOException {
        // given: 의존 관계 없는 단순 Q-class
        writeQClass("com/example", "QSimple", "");

        // when
        QClassScanner scanner = new QClassScanner();
        scanner.scan(List.of(tempDir.toFile()));

        // then
        assertThat(scanner.getInfoMap()).containsKey("QSimple");
        assertThat(scanner.getInfoMap().get("QSimple").pkg).isEqualTo("com.example");
        assertThat(scanner.getInfoMap().get("QSimple").fqcn()).isEqualTo("com.example.QSimple");
    }

    @Test
    @DisplayName("new QXxx( 패턴으로 의존 관계를 감지한다")
    void detectsDependencyFromNewPattern() throws IOException {
        // given: QParent가 QChild를 참조
        writeQClass("com/example", "QParent",
            "    public final QChild child = new QChild(forProperty(\"child\"));");
        writeQClass("com/example", "QChild", "");

        // when
        QClassScanner scanner = new QClassScanner();
        scanner.scan(List.of(tempDir.toFile()));

        // then
        assertThat(scanner.getDependencyGraph().get("QParent")).containsExactly("QChild");
    }

    @Test
    @DisplayName("양방향 참조를 감지한다")
    void detectsBidirectionalReference() throws IOException {
        // given: QA ↔ QB (실제 QCmsBaseEntity ↔ QCmsManager와 같은 구조)
        writeQClass("com/example", "QA",
            "    public final QB b = new QB(forProperty(\"b\"));");
        writeQClass("com/example", "QB",
            "    public final QA a = new QA(forProperty(\"a\"));");

        // when
        QClassScanner scanner = new QClassScanner();
        scanner.scan(List.of(tempDir.toFile()));

        // then
        assertThat(scanner.getDependencyGraph().get("QA")).contains("QB");
        assertThat(scanner.getDependencyGraph().get("QB")).contains("QA");
    }

    @Test
    @DisplayName("자기 자신 참조(new QSelf)는 의존에서 제외된다")
    void selfReferenceIsExcluded() throws IOException {
        // given: QSelf가 자기 자신을 참조 (QueryDSL의 static 필드 초기화 패턴)
        writeQClass("com/example", "QSelf",
            "    public static final QSelf self = new QSelf(\"self\");");

        // when
        QClassScanner scanner = new QClassScanner();
        scanner.scan(List.of(tempDir.toFile()));

        // then: 자기 참조는 순환이 아니므로 그래프에 없어야 함
        assertThat(scanner.getDependencyGraph()).doesNotContainKey("QSelf");
    }

    @Test
    @DisplayName("Q로 시작하지 않는 파일은 무시한다")
    void ignoresNonQFiles() throws IOException {
        // given: 일반 Java 파일
        Path dir = tempDir.resolve("com/example");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SomeEntity.java"),
            "package com.example;\npublic class SomeEntity {}");

        // when
        QClassScanner scanner = new QClassScanner();
        scanner.scan(List.of(tempDir.toFile()));

        // then
        assertThat(scanner.getScannedCount()).isZero();
    }

    @Test
    @DisplayName("여러 디렉토리를 한 번에 스캔한다")
    void scansMultipleDirectories(@TempDir Path anotherDir) throws IOException {
        // given: 서로 다른 디렉토리에 Q-class가 하나씩
        writeQClass("com/a", "QFirst", "");

        Path pkg = anotherDir.resolve("com/b");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("QSecond.java"),
            "package com.b;\npublic class QSecond {}");

        // when
        QClassScanner scanner = new QClassScanner();
        scanner.scan(List.of(tempDir.toFile(), anotherDir.toFile()));

        // then
        assertThat(scanner.getScannedCount()).isEqualTo(2);
        assertThat(scanner.getInfoMap()).containsKeys("QFirst", "QSecond");
    }

    // ── 헬퍼 ──

    /** 최소한의 Q-class 소스 파일을 생성한다 */
    private void writeQClass(String packagePath, String className, String body) throws IOException {
        Path dir = tempDir.resolve(packagePath);
        Files.createDirectories(dir);
        String pkg = packagePath.replace("/", ".");
        String source = "package " + pkg + ";\n"
            + "public class " + className + " {\n"
            + body + "\n"
            + "}\n";
        Files.writeString(dir.resolve(className + ".java"), source);
    }
}
