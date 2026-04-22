package io.github.jsjg73.qclasscycleguard;

import io.github.jsjg73.qclasscycleguard.core.CyclicQClassResourceWriter;
import io.github.jsjg73.qclasscycleguard.core.QClassScanner;
import io.github.jsjg73.qclasscycleguard.core.TarjanScc;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;

/**
 * Q-class 순환 참조를 감지하고, META-INF/cyclic-qclasses.txt를 생성하는 Gradle Task.
 *
 * <h2>동작 흐름</h2>
 * <ol>
 *   <li>{@link QClassScanner}로 Q-class 파일들을 스캔하여 의존 그래프 구축</li>
 *   <li>{@link TarjanScc}로 의존 그래프에서 순환(SCC) 탐지</li>
 *   <li>{@link CyclicQClassResourceWriter}로 META-INF/cyclic-qclasses.txt 생성</li>
 * </ol>
 */
public abstract class QClassCycleGuardTask extends DefaultTask {

    /** Q-class 소스가 있는 디렉토리 목록 */
    @Input
    public abstract ListProperty<File> getQClassDirs();

    /** 리소스 파일(cyclic-qclasses.txt) 출력 디렉토리 */
    @OutputDirectory
    public abstract DirectoryProperty getResourceOutputDir();

    @TaskAction
    public void execute() throws IOException {
        List<File> dirs = getQClassDirs().get();

        if (dirs.isEmpty()) {
            getLogger().lifecycle("Q-class 디렉토리가 없습니다.");
            writeResource(List.of(), new QClassScanner());
            return;
        }

        // 1단계: Q-class 스캔 → 의존 그래프 구축
        QClassScanner scanner = new QClassScanner();
        scanner.scan(dirs);
        getLogger().lifecycle("Q-class {}개 스캔, 의존 관계 {}개 발견",
            scanner.getScannedCount(), scanner.getDependencyCount());

        // 2단계: 순환 탐지
        List<Set<String>> cycles = new TarjanScc(scanner.getDependencyGraph()).findCycles();
        if (cycles.isEmpty()) {
            getLogger().lifecycle("Q-class 순환 참조 없음.");
        } else {
            for (Set<String> scc : cycles) {
                getLogger().warn("Q-class 순환 감지: {}", String.join(" <-> ", scc));
            }
        }

        // 3단계: 리소스 파일 생성
        writeResource(cycles, scanner);
    }

    private void writeResource(List<Set<String>> cycles, QClassScanner scanner) throws IOException {
        Path resourceDir = getResourceOutputDir().getAsFile().get().toPath();
        CyclicQClassResourceWriter writer = new CyclicQClassResourceWriter(cycles, scanner.getInfoMap());
        Path resourceFile = writer.write(resourceDir);
        getLogger().lifecycle("리소스 생성됨: {}", resourceFile);
    }
}