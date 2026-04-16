package io.github.jsjg73.qclasscycleguard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class CyclicQClassResourceWriterTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("T1: cycle이 있으면 META-INF/cyclic-qclasses.txt에 FQCN이 한 줄씩 기록된다")
    void writesCyclicFqcnsToResourceFile() throws IOException {
        // given: QA ↔ QB 순환
        Map<String, QClassInfo> infoMap = Map.of(
            "QA", new QClassInfo("QA", "com.example.entity"),
            "QB", new QClassInfo("QB", "com.example.entity")
        );
        List<Set<String>> cycles = List.of(Set.of("QA", "QB"));

        // when
        CyclicQClassResourceWriter writer = new CyclicQClassResourceWriter(cycles, infoMap);
        Path generated = writer.write(tempDir);

        // then
        assertThat(generated).exists();
        assertThat(generated.toString()).endsWith("META-INF/cyclic-qclasses.txt");

        List<String> lines = Files.readAllLines(generated);
        assertThat(lines).containsExactly(
            "com.example.entity.QA",
            "com.example.entity.QB"
        );
    }

    @Test
    @DisplayName("T2: cycle이 없으면 빈 파일이 생성된다")
    void writesEmptyFileWhenNoCycles() throws IOException {
        // given
        List<Set<String>> noCycles = List.of();
        Map<String, QClassInfo> emptyInfo = Map.of();

        // when
        CyclicQClassResourceWriter writer = new CyclicQClassResourceWriter(noCycles, emptyInfo);
        Path generated = writer.write(tempDir);

        // then
        assertThat(generated).exists();
        assertThat(Files.readString(generated)).isEmpty();
    }

    @Test
    @DisplayName("T3: 여러 cycle 그룹이 있으면 모든 FQCN이 정렬되어 기록된다")
    void writesAllFqcnsSortedFromMultipleCycles() throws IOException {
        // given: 두 개의 순환 그룹
        Map<String, QClassInfo> infoMap = Map.of(
            "QA", new QClassInfo("QA", "com.z.entity"),
            "QB", new QClassInfo("QB", "com.z.entity"),
            "QX", new QClassInfo("QX", "com.a.entity"),
            "QY", new QClassInfo("QY", "com.a.entity")
        );
        List<Set<String>> cycles = List.of(
            Set.of("QA", "QB"),
            Set.of("QX", "QY")
        );

        // when
        CyclicQClassResourceWriter writer = new CyclicQClassResourceWriter(cycles, infoMap);
        Path generated = writer.write(tempDir);

        // then: 패키지명 기준으로 정렬
        List<String> lines = Files.readAllLines(generated);
        assertThat(lines).containsExactly(
            "com.a.entity.QX",
            "com.a.entity.QY",
            "com.z.entity.QA",
            "com.z.entity.QB"
        );
    }
}
