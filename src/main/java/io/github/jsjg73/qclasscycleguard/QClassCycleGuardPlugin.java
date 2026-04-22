package io.github.jsjg73.qclasscycleguard;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
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
 * META-INF/cyclic-qclasses.txt 리소스 파일을 생성한다.</p>
 *
 * <h2>사용법</h2>
 * <pre>
 * // build.gradle
 * plugins {
 *     id 'com.github.jsjg73.qclass-cycle-guard-plugin'
 * }
 * </pre>
 *
 * <h2>동작 흐름</h2>
 * <ol>
 *   <li>compileJava 완료 (annotation processing으로 Q-class 생성)</li>
 *   <li>detectQClassCycle task가 Q-class 소스를 스캔하여 순환 탐지</li>
 *   <li>META-INF/cyclic-qclasses.txt 리소스 파일 생성</li>
 *   <li>copyQClassCycleGuardResource task가 classes 디렉토리에 복사</li>
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
        // 1. detectQClassCycle task 등록
        registerTask(project);

        // 2. 프로젝트 평가 완료 후 (= 모든 의존성이 확정된 후) 세부 설정
        project.afterEvaluate(this::configureAfterEvaluate);
    }

    private void registerTask(Project project) {
        project.getTasks().register("detectQClassCycle", QClassCycleGuardTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Q-class 순환 참조를 감지하고 META-INF/cyclic-qclasses.txt를 생성합니다");

            // 리소스 파일(cyclic-qclasses.txt)이 저장될 위치
            task.getResourceOutputDir().set(
                project.getLayout().getBuildDirectory().dir("generated/resources/qclass-cycle-guard")
            );
        });
    }

    private void configureAfterEvaluate(Project project) {
        List<File> qDirs = collectQClassDirs(project);

        // task에 스캔 대상 디렉토리 전달
        project.getTasks().named("detectQClassCycle", QClassCycleGuardTask.class, task -> {
            task.getQClassDirs().set(qDirs);
            task.dependsOn("compileJava");
        });

        // 리소스 파일을 classes 디렉토리에 복사하는 task
        project.getTasks().register("copyQClassCycleGuardResource", Copy.class, task -> {
            task.dependsOn("detectQClassCycle");
            task.from(project.getLayout().getBuildDirectory().dir("generated/resources/qclass-cycle-guard"));
            task.into(project.getTasks().named("compileJava", JavaCompile.class).get()
                .getDestinationDirectory());
        });

        // classes task가 리소스 복사를 포함하도록 설정
        project.getTasks().named("classes", task -> {
            task.dependsOn("copyQClassCycleGuardResource");
        });
    }

    /**
     * 현재 모듈의 Q-class 디렉토리를 수집한다.
     */
    private List<File> collectQClassDirs(Project project) {
        List<File> qDirs = new ArrayList<>();

        for (String path : Q_CLASS_PATHS) {
            qDirs.add(new File(project.getProjectDir(), path));
        }

        return qDirs;
    }
}