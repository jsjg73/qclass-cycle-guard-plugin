package io.github.jsjg73.qclasscycleguard;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Q-class 순환 참조 감지 플러그인.
 *
 * <p>QueryDSL이 생성하는 Q-class들 사이에 순환 참조가 있으면
 * 멀티스레드 환경에서 class initialization deadlock이 발생할 수 있다.
 * 이 플러그인은 빌드 시점에 순환을 감지하고,
 * 앱 시작 시 싱글스레드로 미리 로딩하는 초기화 코드를 자동 생성한다.</p>
 *
 * <h3>사용법</h3>
 * <pre>
 * // build.gradle
 * plugins {
 *     id 'qclass-cycle-guard'
 * }
 * qclassCycleGuard {
 *     configPackage = 'com.example.config'
 * }
 * </pre>
 *
 * <h3>동작 흐름</h3>
 * <ol>
 *   <li>compileJava 완료 (annotation processing으로 Q-class 생성)</li>
 *   <li>detectQClassCycle task가 자기 모듈의 Q-class 소스를 스캔하여 순환 탐지</li>
 *   <li>순환이 있으면 QClassInitializer.java를 자동 생성</li>
 *   <li>compileQClassInitializer task가 생성된 코드를 컴파일</li>
 * </ol>
 */
public class QClassCycleGuardPlugin implements Plugin<Project> {

    /**
     * Q-class가 생성되는 알려진 디렉토리 경로들.
     * QueryDSL 버전이나 빌드 설정에 따라 경로가 다를 수 있다.
     */
    private static final String[] Q_CLASS_PATHS = {
        "build/generated/querydsl",                                  // querydsl-apt 기본 경로
        "build/generated/sources/annotationProcessor/java/main"      // Gradle 기본 AP 경로
    };

    @Override
    public void apply(Project project) {
        // 1. extension 등록
        QClassCycleGuardExtension extension = project.getExtensions()
            .create("qclassCycleGuard", QClassCycleGuardExtension.class);

        // 2. detectQClassCycle task 등록
        registerTask(project);

        // 3. 프로젝트 평가 완료 후 (= 모든 의존성이 확정된 후) 세부 설정
        project.afterEvaluate(p -> configureAfterEvaluate(p, extension));
    }

    private void registerTask(Project project) {
        project.getTasks().register("detectQClassCycle", QClassCycleGuardTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Q-class 순환 참조를 감지하고 초기화 코드를 생성합니다");

            // 생성된 초기화 코드가 저장될 위치
            task.getOutputDir().set(
                project.getLayout().getBuildDirectory().dir("generated/sources/qclass-cycle-guard/java/main")
            );
            // 리소스 파일(cyclic-qclasses.txt)이 저장될 위치
            task.getResourceOutputDir().set(
                project.getLayout().getBuildDirectory().dir("generated/resources/qclass-cycle-guard")
            );
        });
    }

    private void configureAfterEvaluate(Project project, QClassCycleGuardExtension extension) {
        if (!extension.getConfigPackage().isPresent()) {
            throw new IllegalStateException(
                "qclassCycleGuard.configPackage가 설정되지 않았습니다. build.gradle에 설정해주세요.\n"
                + "예: qclassCycleGuard { configPackage = 'com.example.config' }");
        }

        String configPackage = extension.getConfigPackage().get();

        // Q-class가 있는 디렉토리 목록 수집
        List<File> qDirs = collectQClassDirs(project);

        // task에 스캔 대상 디렉토리와 패키지 전달
        project.getTasks().named("detectQClassCycle", QClassCycleGuardTask.class, task -> {
            task.getQClassDirs().set(qDirs);
            task.getConfigPackage().set(configPackage);
        });

        JavaPluginExtension javaExt = project.getExtensions().getByType(JavaPluginExtension.class);
        FileCollection mainCompileClasspath = javaExt.getSourceSets().getByName("main").getCompileClasspath();

        // compileJava 완료 후 Q-class를 스캔하여 순환 감지 + 코드 생성
        project.getTasks().named("detectQClassCycle", QClassCycleGuardTask.class, task -> {
            task.dependsOn("compileJava");
        });

        // 생성된 QClassInitializer.java를 컴파일하는 별도 task
        project.getTasks().register("compileQClassInitializer", JavaCompile.class, task -> {
            task.dependsOn("detectQClassCycle");
            task.source(project.getLayout().getBuildDirectory().dir("generated/sources/qclass-cycle-guard/java/main"));
            task.setClasspath(mainCompileClasspath);
            task.getDestinationDirectory().set(
                project.getTasks().named("compileJava", JavaCompile.class).get().getDestinationDirectory()
            );
        });

        // 리소스 파일을 classes 디렉토리에 복사하는 task
        // (소스셋 리소스로 등록하면 processResources와 출력이 겹쳐 Gradle이 충돌을 감지함)
        project.getTasks().register("copyQClassCycleGuardResource", Copy.class, task -> {
            task.dependsOn("detectQClassCycle");
            task.from(project.getLayout().getBuildDirectory().dir("generated/resources/qclass-cycle-guard"));
            task.into(project.getTasks().named("compileJava", JavaCompile.class).get()
                .getDestinationDirectory());
        });

        // classes task가 compileQClassInitializer와 리소스 복사를 포함하도록 설정
        project.getTasks().named("classes", task -> {
            task.dependsOn("compileQClassInitializer");
            task.dependsOn("copyQClassCycleGuardResource");
        });

        // IntelliJ가 생성된 QClassInitializer 소스를 인식하도록 main 소스셋에 등록
        javaExt.getSourceSets().getByName("main").getJava()
            .srcDir(project.getLayout().getBuildDirectory().dir("generated/sources/qclass-cycle-guard/java/main"));
    }

    /**
     * 현재 모듈의 Q-class 디렉토리를 수집한다.
     */
    private List<File> collectQClassDirs(Project project) {
        List<File> qDirs = new ArrayList<>();

        for (String path : Q_CLASS_PATHS) {
            File dir = new File(project.getProjectDir(), path);
            if (dir.exists()) {
                qDirs.add(dir);
            }
        }

        return qDirs;
    }
}
