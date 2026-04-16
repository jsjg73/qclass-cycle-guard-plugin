package io.github.jsjg73.qclasscycleguard;

import java.util.*;

/**
 * Tarjan의 강한 연결 요소(Strongly Connected Components, SCC) 알고리즘으로
 * 방향 그래프에서 순환(cycle)을 찾는다.
 *
 * <h3>핵심 개념</h3>
 * <p>"강한 연결 요소"란 그래프에서 서로 도달 가능한 노드들의 그룹이다.
 * 예를 들어 A→B→C→A 이면 {A, B, C}가 하나의 SCC이다.
 * SCC의 크기가 2 이상이면 순환이 존재한다는 의미이다.</p>
 *
 * <h3>사용 예시</h3>
 * <pre>
 * Map&lt;String, Set&lt;String&gt;&gt; graph = Map.of(
 *     "QA", Set.of("QB"),
 *     "QB", Set.of("QA")  // QA ↔ QB 순환
 * );
 * List&lt;Set&lt;String&gt;&gt; cycles = new TarjanScc(graph).findCycles();
 * // cycles = [{QA, QB}]
 * </pre>
 */
class TarjanScc {

    private final Map<String, Set<String>> graph;

    /** DFS 탐색 순서 번호 (방문할 때마다 1씩 증가) */
    private int index = 0;

    /** 각 노드가 처음 방문된 순서 */
    private final Map<String, Integer> indices = new HashMap<>();

    /**
     * 각 노드에서 도달 가능한 가장 작은 index 값.
     * lowlink == index이면 그 노드가 SCC의 루트이다.
     */
    private final Map<String, Integer> lowlinks = new HashMap<>();

    /** 현재 DFS 스택에 있는 노드들 (빠른 조회용) */
    private final Set<String> onStack = new HashSet<>();

    /** DFS 스택 */
    private final Deque<String> stack = new ArrayDeque<>();

    /** 발견된 SCC 목록 */
    private final List<Set<String>> result = new ArrayList<>();

    TarjanScc(Map<String, Set<String>> graph) {
        this.graph = graph;
    }

    /**
     * 순환(크기 2 이상의 SCC)을 찾아서 반환한다.
     * 순환이 없으면 빈 리스트를 반환한다.
     */
    List<Set<String>> findCycles() {
        // 아직 방문하지 않은 모든 노드에서 DFS 시작
        for (String node : graph.keySet()) {
            if (!indices.containsKey(node)) {
                strongConnect(node);
            }
        }

        // 크기 1인 SCC는 자기 자신만 포함 → 순환 아님
        result.removeIf(scc -> scc.size() < 2);
        return result;
    }

    /**
     * Tarjan DFS의 핵심 로직.
     *
     * <p>각 노드를 방문하면서:
     * <ol>
     *   <li>방문 순서(index)를 기록하고 스택에 넣는다</li>
     *   <li>이웃 노드들을 재귀적으로 방문한다</li>
     *   <li>이웃의 lowlink 값으로 자신의 lowlink를 갱신한다</li>
     *   <li>lowlink == index이면, 스택에서 자신까지 꺼내서 SCC로 묶는다</li>
     * </ol>
     */
    private void strongConnect(String v) {
        // 이 노드에 방문 순서를 부여하고 스택에 넣는다
        indices.put(v, index);
        lowlinks.put(v, index);
        index++;
        stack.push(v);
        onStack.add(v);

        // 이웃(의존하는 Q-class)들을 탐색
        Set<String> deps = graph.getOrDefault(v, Set.of());
        for (String w : deps) {
            if (!indices.containsKey(w)) {
                // 아직 미방문 → 재귀 탐색
                strongConnect(w);
                lowlinks.put(v, Math.min(lowlinks.get(v), lowlinks.get(w)));
            } else if (onStack.contains(w)) {
                // 이미 스택에 있음 → 순환 경로 발견
                lowlinks.put(v, Math.min(lowlinks.get(v), indices.get(w)));
            }
        }

        // lowlink == index이면, 이 노드가 SCC의 루트
        // → 스택에서 이 노드까지 꺼내서 하나의 SCC로 묶는다
        if (lowlinks.get(v).equals(indices.get(v))) {
            Set<String> scc = new LinkedHashSet<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (!w.equals(v));
            result.add(scc);
        }
    }
}
