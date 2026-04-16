package io.github.jsjg73.qclasscycleguard;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

/**
 * Q-class 소스 파일들을 스캔하여 의존 그래프를 구축한다.
 *
 * <p>QueryDSL이 생성하는 Q-class 소스(.java)를 읽어서,
 * 각 Q-class가 어떤 다른 Q-class를 참조하는지 파악한다.</p>
 *
 * <p>예: QCmsBaseEntity.java 안에 {@code new QCmsManager(...)} 가 있으면
 * QCmsBaseEntity → QCmsManager 의존이 있다고 판단한다.</p>
 */
class QClassScanner {

    /**
     * Q-class 소스에서 다른 Q-class 생성자 호출을 찾는 패턴.
     * 예: "new QCmsManager(" → "QCmsManager"을 캡처
     */
    private static final Pattern NEW_Q_CLASS = Pattern.compile("new\\s+(Q\\w+)\\(");

    /** Q-class 이름 → 정보 (패키지, FQCN 등) */
    private final Map<String, QClassInfo> infoMap = new HashMap<>();

    /** Q-class 이름 → 의존하는 Q-class 이름들 (의존 그래프) */
    private final Map<String, Set<String>> dependencyGraph = new HashMap<>();

    /**
     * 지정된 디렉토리들에서 Q-class 파일을 스캔한다.
     */
    void scan(List<File> directories) throws IOException {
        for (File dir : directories) {
            if (!dir.exists()) continue;
            scanDirectory(dir);
        }
    }

    Map<String, QClassInfo> getInfoMap() {
        return Collections.unmodifiableMap(infoMap);
    }

    Map<String, Set<String>> getDependencyGraph() {
        return Collections.unmodifiableMap(dependencyGraph);
    }

    int getScannedCount() {
        return infoMap.size();
    }

    int getDependencyCount() {
        return dependencyGraph.size();
    }

    // ── private ──

    private void scanDirectory(File dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            paths.filter(this::isQClassFile)
                 .forEach(this::parseQClassFile);
        }
    }

    /** Q로 시작하는 .java 파일인지 확인 */
    private boolean isQClassFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith("Q") && fileName.endsWith(".java");
    }

    /** Q-class 파일 하나를 읽어서 정보와 의존 관계를 추출 */
    private void parseQClassFile(Path path) {
        try {
            String className = path.getFileName().toString().replace(".java", "");
            List<String> lines = Files.readAllLines(path);

            String pkg = extractPackage(lines);
            infoMap.put(className, new QClassInfo(className, pkg));

            Set<String> deps = extractDependencies(lines, className);
            if (!deps.isEmpty()) {
                dependencyGraph.put(className, deps);
            }
        } catch (IOException e) {
            throw new RuntimeException("Q-class 파일 읽기 실패: " + path, e);
        }
    }

    /** 소스 파일의 package 선언에서 패키지명 추출 */
    private String extractPackage(List<String> lines) {
        return lines.stream()
            .filter(line -> line.startsWith("package "))
            .map(line -> line.replace("package ", "").replace(";", "").trim())
            .findFirst()
            .orElse("");
    }

    /** 소스 파일에서 "new QXxx(" 패턴을 찾아 의존하는 Q-class 목록 추출 */
    private Set<String> extractDependencies(List<String> lines, String self) {
        Set<String> deps = new HashSet<>();
        for (String line : lines) {
            Matcher m = NEW_Q_CLASS.matcher(line);
            while (m.find()) {
                deps.add(m.group(1));
            }
        }
        deps.remove(self); // 자기 자신 참조는 순환이 아님
        return deps;
    }
}
