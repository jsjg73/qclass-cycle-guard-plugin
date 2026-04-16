package io.github.jsjg73.qclasscycleguard;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 순환 참조가 감지된 Q-class FQCN 목록을 리소스 파일로 기록한다.
 *
 * <p>생성되는 파일: {@code META-INF/cyclic-qclasses.txt}
 * <br>포맷: FQCN 한 줄씩, 알파벳 정렬</p>
 *
 * <p>런타임에 {@code ClassLoader.getResources("META-INF/cyclic-qclasses.txt")}로
 * 여러 모듈의 파일을 한꺼번에 읽을 수 있다.</p>
 */
class CyclicQClassResourceWriter {

    private final List<Set<String>> cycles;
    private final Map<String, QClassInfo> infoMap;

    CyclicQClassResourceWriter(List<Set<String>> cycles, Map<String, QClassInfo> infoMap) {
        this.cycles = cycles;
        this.infoMap = infoMap;
    }

    Path write(Path outputDir) throws IOException {
        List<String> fqcns = cycles.stream()
            .flatMap(Set::stream)
            .map(infoMap::get)
            .filter(Objects::nonNull)
            .map(QClassInfo::fqcn)
            .sorted()
            .distinct()
            .collect(Collectors.toList());

        Path file = outputDir.resolve("META-INF/cyclic-qclasses.txt");
        Files.createDirectories(file.getParent());
        Files.write(file, fqcns);
        return file;
    }
}
