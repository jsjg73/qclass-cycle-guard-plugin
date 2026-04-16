package io.github.jsjg73.qclasscycleguard;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 순환 참조가 감지된 Q-class들을 싱글스레드로 미리 로딩하는
 * {@code QClassInitializer.java} 소스 코드를 생성한다.
 *
 * <p>생성되는 코드는 {@code Class.forName()}으로 Q-class를 싱글스레드에서 로딩한다.
 * 싱글스레드에서 로딩하면 JVM class initialization lock 경합이 없으므로 deadlock이 발생하지 않는다.</p>
 */
class InitializerCodeGenerator {

    private final List<Set<String>> cycles;
    private final Map<String, QClassInfo> infoMap;
    private final String configPackage;

    /**
     * @param cycles        감지된 순환 그룹들
     * @param infoMap       Q-class 이름 → 정보 맵
     * @param configPackage 생성될 QClassInitializer의 패키지 (예: "com.example.config")
     */
    InitializerCodeGenerator(List<Set<String>> cycles, Map<String, QClassInfo> infoMap, String configPackage) {
        this.cycles = cycles;
        this.infoMap = infoMap;
        this.configPackage = configPackage;
    }

    /**
     * QClassInitializer.java를 생성하고, 생성된 파일 경로를 반환한다.
     *
     * @param outputBaseDir 생성된 소스의 루트 디렉토리 (예: build/generated/sources/.../java/main)
     */
    Path generate(Path outputBaseDir) throws IOException {
        List<String> fqcns = collectCyclicFqcns();
        String code = buildSourceCode(configPackage, fqcns);

        return writeFile(outputBaseDir, configPackage, code);
    }

    /** 순환에 포함된 Q-class들의 FQCN 반환 (테스트/로깅용) */
    List<String> collectCyclicFqcns() {
        return cycles.stream()
            .flatMap(Set::stream)
            .map(infoMap::get)
            .filter(Objects::nonNull)
            .map(QClassInfo::fqcn)
            .sorted()
            .distinct()
            .collect(Collectors.toList());
    }

    // ── private ──

    private String buildSourceCode(String configPkg, List<String> fqcns) {
        String classNames = fqcns.stream()
            .map(fqcn -> "            \"" + fqcn + "\"")
            .collect(Collectors.joining(",\n"));

        String cycleDesc = cycles.stream()
            .map(scc -> String.join(" <-> ", scc))
            .collect(Collectors.joining("; "));

        return "package " + configPkg + ";\n"
            + "\n"
            + "/**\n"
            + " * 자동 생성됨 - 직접 수정하지 마세요.\n"
            + " *\n"
            + " * <p>Q-class 순환 참조로 인한 class initialization deadlock을 방지하기 위해\n"
            + " * 싱글스레드에서 미리 클래스를 로딩한다.</p>\n"
            + " *\n"
            + " * <p>감지된 순환 참조: " + cycleDesc + "</p>\n"
            + " */\n"
            + "public class QClassInitializer {\n"
            + "\n"
            + "    private static final String[] CYCLIC_Q_CLASSES = {\n"
            + classNames + "\n"
            + "    };\n"
            + "\n"
            + "    public static void init() {\n"
            + "        for (String className : CYCLIC_Q_CLASSES) {\n"
            + "            try {\n"
            + "                Class.forName(className);\n"
            + "            } catch (ClassNotFoundException e) {\n"
            + "                System.err.println(\"[QClassInitializer] Q-class 초기화 실패: \" + className);\n"
            + "            }\n"
            + "        }\n"
            + "        System.out.println(\"[QClassInitializer] Q-class 순환 참조 초기화 완료: \"\n"
            + "            + CYCLIC_Q_CLASSES.length + \"건\");\n"
            + "    }\n"
            + "\n"
            + "    private QClassInitializer() {}\n"
            + "}\n";
    }

    private Path writeFile(Path outputBaseDir, String configPkg, String code) throws IOException {
        Path dir = outputBaseDir.resolve(configPkg.replace(".", "/"));
        Files.createDirectories(dir);
        Path file = dir.resolve("QClassInitializer.java");
        Files.writeString(file, code);
        return file;
    }

}
