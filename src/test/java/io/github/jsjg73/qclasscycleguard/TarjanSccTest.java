package io.github.jsjg73.qclasscycleguard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TarjanScc} 순환 탐지 알고리즘 테스트.
 *
 * <p>Tarjan SCC는 방향 그래프에서 "강한 연결 요소"(서로 도달 가능한 노드 그룹)를 찾는다.
 * 크기 2 이상인 SCC가 곧 순환이다.</p>
 */
class TarjanSccTest {

    @Test
    @DisplayName("의존 관계가 없으면 순환도 없다")
    void noDependencies_noCycles() {
        // given: 노드만 있고 간선이 없는 그래프
        Map<String, Set<String>> graph = Map.of();

        // when
        List<Set<String>> cycles = new TarjanScc(graph).findCycles();

        // then
        assertThat(cycles).isEmpty();
    }

    @Test
    @DisplayName("단방향 의존은 순환이 아니다 (A → B)")
    void oneWayDependency_noCycle() {
        // given: QA → QB (QB는 QA를 참조하지 않음)
        Map<String, Set<String>> graph = Map.of(
            "QA", Set.of("QB")
        );

        // when
        List<Set<String>> cycles = new TarjanScc(graph).findCycles();

        // then
        assertThat(cycles).isEmpty();
    }

    @Test
    @DisplayName("양방향 의존은 순환이다 (A ↔ B)")
    void bidirectionalDependency_isCycle() {
        // given: QCmsBaseEntity ↔ QCmsManager (실제 프로젝트의 사례)
        Map<String, Set<String>> graph = Map.of(
            "QCmsBaseEntity", Set.of("QCmsManager"),
            "QCmsManager", Set.of("QCmsBaseEntity")
        );

        // when
        List<Set<String>> cycles = new TarjanScc(graph).findCycles();

        // then: 하나의 순환 그룹에 두 클래스가 포함
        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0)).containsExactlyInAnyOrder("QCmsBaseEntity", "QCmsManager");
    }

    @Test
    @DisplayName("3개 노드 순환 (A → B → C → A)")
    void threeNodeCycle() {
        // given
        Map<String, Set<String>> graph = Map.of(
            "QA", Set.of("QB"),
            "QB", Set.of("QC"),
            "QC", Set.of("QA")
        );

        // when
        List<Set<String>> cycles = new TarjanScc(graph).findCycles();

        // then
        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0)).containsExactlyInAnyOrder("QA", "QB", "QC");
    }

    @Test
    @DisplayName("독립된 순환 2개가 각각 감지된다")
    void twoIndependentCycles() {
        // given: {QA ↔ QB}와 {QC ↔ QD} 두 개의 독립 순환
        Map<String, Set<String>> graph = Map.of(
            "QA", Set.of("QB"),
            "QB", Set.of("QA"),
            "QC", Set.of("QD"),
            "QD", Set.of("QC")
        );

        // when
        List<Set<String>> cycles = new TarjanScc(graph).findCycles();

        // then: 두 개의 순환 그룹
        assertThat(cycles).hasSize(2);
    }

    @Test
    @DisplayName("순환과 비순환 노드가 섞여 있어도 순환만 감지한다")
    void mixedGraph_onlyCyclesDetected() {
        // given: QA ↔ QB 순환 + QC → QA 단방향 (QC는 순환에 포함되지 않음)
        Map<String, Set<String>> graph = Map.of(
            "QA", Set.of("QB"),
            "QB", Set.of("QA"),
            "QC", Set.of("QA")
        );

        // when
        List<Set<String>> cycles = new TarjanScc(graph).findCycles();

        // then: QA, QB만 순환, QC는 제외
        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0)).containsExactlyInAnyOrder("QA", "QB");
    }
}
