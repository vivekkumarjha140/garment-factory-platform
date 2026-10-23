package com.garmentfactory.catalog.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The category hierarchy in memory (a catalog has tens of categories, not millions). Roots have depth 1. */
public final class CategoryTree {

    public record Node(Long id, Long parentId) {
    }

    private static final int GUARD = 1000;

    private final Map<Long, Long> parentOf = new HashMap<>();
    private final Map<Long, List<Long>> childrenOf = new HashMap<>();

    public CategoryTree(Collection<Node> nodes) {
        for (Node node : nodes) {
            parentOf.put(node.id(), node.parentId());
            if (node.parentId() != null) {
                childrenOf.computeIfAbsent(node.parentId(), key -> new ArrayList<>()).add(node.id());
            }
        }
    }

    /** Number of levels from the root down to this category, counting both. */
    public int depth(Long id) {
        int depth = 1;
        Long parent = parentOf.get(id);
        for (int steps = 0; parent != null && steps < GUARD; steps++) {
            depth++;
            parent = parentOf.get(parent);
        }
        return depth;
    }

    /** Number of levels in the subtree under (and including) this category. A leaf has height 1. */
    public int height(Long id) {
        int tallest = 0;
        for (Long child : childrenOf.getOrDefault(id, List.of())) {
            tallest = Math.max(tallest, height(child));
        }
        return tallest + 1;
    }

    /** True if {@code candidate} is {@code ancestor} itself or lies somewhere below it. */
    public boolean isSelfOrDescendant(Long candidate, Long ancestor) {
        Long current = candidate;
        for (int steps = 0; current != null && steps < GUARD; steps++) {
            if (current.equals(ancestor)) {
                return true;
            }
            current = parentOf.get(current);
        }
        return false;
    }

    /** The category and every category below it, so choosing "Men" also shows products in "Men / T-Shirts". */
    public Set<Long> descendantsAndSelf(Long id) {
        Set<Long> result = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(id);
        while (!queue.isEmpty() && result.size() < GUARD) {
            Long current = queue.poll();
            if (result.add(current)) {
                queue.addAll(childrenOf.getOrDefault(current, List.of()));
            }
        }
        return result;
    }

    public Long parentOf(Long id) {
        return parentOf.get(id);
    }
}