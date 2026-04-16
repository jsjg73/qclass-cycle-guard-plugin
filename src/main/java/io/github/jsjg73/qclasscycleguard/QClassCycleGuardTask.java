package io.github.jsjg73.qclasscycleguard;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Q-class 순환 참조를 감지하고, 초기화 코드를 생성하는 Gradle Task.
 *
 * <h3>동작 흐름</h3>
 * <ol>
 *   <li>{@link QClassScanner}로 Q-class 파일들을 스캔하여 의존 그래프 구축</li>
 *   <li>{@link TarjanScc}로 의존 그래프에서 순환(SCC) 탐지</li>
 *   <li>{@link InitializerCodeGenerator}로 QClassInitializer.java 생성</li>
 * </ol>
 *
 * <p>순환이 없거나 스캔 대상이 없어도 빈 {@code init()} 메서드를 가진
 * QClassInitializer를 항상 생성한다. 컴파일 시점에 이 클래스를 참조하는
 * 코드가 있을 수 있기 때문이다.</p>
 */
public abstract class QClassCycleGuardTask extends DefaultTask {

    /** Q-class 소스가 있는 디렉토리 목록 */
    @Input
    public abstract ListProperty<File> getQClassDirs();

    /** 생성될 QClassInitializer의 패키지 (예: "com.example.config") */
    @Input
    public abstract Property<String> getConfigPackage();

    /** 생성된 소스의 출력 디렉토리 */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /** 리소스 파일(cyclic-qclasses.txt) 출력 디렉토리 */
    @OutputDirectory
    public abstract DirectoryProperty getResourceOutputDir();

    @TaskAction
    public void execute() throws IOException {
        String configPkg = getConfigPackage().get();
        Path outputBaseDir = getOutputDir().getAsFile().get().toPath();
        List<File> dirs = getQClassDirs().get();

        if (dirs.isEmpty()) {
            getLogger().lifecycle("Q-class 디렉토리가 없습니다. no-op QClassInitializer를 생성합니다.");
            generateNoOp(configPkg, outputBaseDir);
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
            getLogger().lifecycle("Q-class 순환 참조 없음. no-op QClassInitializer를 생성합니다.");
            generateNoOp(configPkg, outputBaseDir);
            return;
        }

        for (Set<String> scc : cycles) {
            getLogger().warn("Q-class 순환 감지: {}", String.join(" <-> ", scc));
        }

        // 3단계: 초기화 코드 생성
        InitializerCodeGenerator generator = new InitializerCodeGenerator(cycles, scanner.getInfoMap(), configPkg);
        Path generated = generator.generate(outputBaseDir);

        getLogger().lifecycle("생성됨: {}", generated);
        getLogger().lifecycle("초기화 대상: {}개 Q-class", generator.collectCyclicFqcns().size());

        // 4단계: 리소스 파일 생성
        writeResource(cycles, scanner.getInfoMap());
    }

    /**
     * 순환이 없거나 스캔 대상이 없을 때, 빈 init() 메서드만 있는 QClassInitializer를 생성한다.
     * 컴파일 오류를 방지하기 위함.
     */
    private void generateNoOp(String configPkg, Path outputBaseDir) throws IOException {
        InitializerCodeGenerator generator = new InitializerCodeGenerator(
            List.of(), Map.of(), configPkg);
        Path generated = generator.generate(outputBaseDir);
        getLogger().lifecycle("생성됨 (no-op): {}", generated);

        writeResource(List.of(), Map.of());
    }

    private void writeResource(List<Set<String>> cycles, Map<String, QClassInfo> infoMap) throws IOException {
        Path resourceDir = getResourceOutputDir().getAsFile().get().toPath();
        CyclicQClassResourceWriter writer = new CyclicQClassResourceWriter(cycles, infoMap);
        Path resourceFile = writer.write(resourceDir);
        getLogger().lifecycle("리소스 생성됨: {}", resourceFile);
    }
}
