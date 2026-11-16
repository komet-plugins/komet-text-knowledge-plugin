/*
 * Copyright © 2026 Knowledge Graphlet / IKE Network
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package network.ike.komet.text.concordance;

import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * The part of the hierarchy a concept is under: the name of its ancestor just below a root of a
 * navigation, a root being a concept with no parents. A concept with no parents of its own is
 * counted as {@value #NO_PARENTS}, which is every concept of a knowledge base whose hierarchy has
 * not been loaded or classified. A concept under several such
 * ancestors is counted under the first by name, so the same concept is always counted the same
 * way. Results are kept, since a collection names the same concepts many times.
 */
public final class Branches implements LongFunction<String> {

    /** What a concept with no parents is counted as. */
    public static final String NO_PARENTS = "(no parents)";

    private final NavigationCalculator navigation;
    private final LongFunction<String> names;
    private final Map<Long, String> known = new HashMap<>();

    /**
     * The parts of the hierarchy under a navigation.
     *
     * @param navigation the navigation that decides each concept's parents, inferred or stated
     * @param names      the name a part of the hierarchy is reported by, by its concept's nid
     */
    public Branches(NavigationCalculator navigation, LongFunction<String> names) {
        this.navigation = navigation;
        this.names = names;
    }

    /**
     * The part of the hierarchy a concept is under.
     *
     * @param conceptNid the concept's nid
     * @return the name of its ancestor just below a root, or {@value #NO_PARENTS}
     */
    @Override
    public String apply(long conceptNid) {
        return known.computeIfAbsent(conceptNid, this::branch);
    }

    private String branch(long conceptNid) {
        if (parents(conceptNid).isEmpty()) {
            return NO_PARENTS;
        }
        List<String> branches = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(conceptNid);
        while (!queue.isEmpty()) {
            long nid = queue.removeFirst();
            if (!seen.add(nid)) {
                continue;
            }
            List<Long> parents = parents(nid);
            boolean belowRoot = !parents.isEmpty();
            for (long parent : parents) {
                if (!parents(parent).isEmpty()) {
                    belowRoot = false;
                }
            }
            if (belowRoot) {
                branches.add(name(nid));
            } else {
                queue.addAll(parents);
            }
        }
        if (branches.isEmpty()) {
            return name(conceptNid);
        }
        branches.sort(String::compareTo);
        return branches.getFirst();
    }

    private List<Long> parents(long conceptNid) {
        LongIdList parents = navigation.parentsOf(conceptNid);
        List<Long> list = new ArrayList<>(parents.size());
        for (long parent : parents.toArray()) {
            list.add(parent);
        }
        return list;
    }

    private String name(long conceptNid) {
        return names.apply(conceptNid);
    }
}
