package com.garmentfactory.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.catalog.service.CategoryTree;
import com.garmentfactory.catalog.service.CategoryTree.Node;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryTreeTest {

    // 1 (Men) -> 2 (Tops) -> 3 (Printed);  1 -> 4 (Bottoms);  5 (Women) is another root
    private final CategoryTree tree = new CategoryTree(List.of(
            new Node(1L, null), new Node(2L, 1L), new Node(3L, 2L), new Node(4L, 1L), new Node(5L, null)));

    @Test
    void depthCountsLevelsFromTheRoot() {
        assertThat(tree.depth(1L)).isEqualTo(1);
        assertThat(tree.depth(2L)).isEqualTo(2);
        assertThat(tree.depth(3L)).isEqualTo(3);
        assertThat(tree.depth(5L)).isEqualTo(1);
    }

    @Test
    void heightCountsLevelsOfTheSubtree() {
        assertThat(tree.height(1L)).isEqualTo(3);
        assertThat(tree.height(2L)).isEqualTo(2);
        assertThat(tree.height(3L)).isEqualTo(1);
        assertThat(tree.height(5L)).isEqualTo(1);
    }

    @Test
    void detectsSelfAndDescendants() {
        assertThat(tree.isSelfOrDescendant(1L, 1L)).isTrue();
        assertThat(tree.isSelfOrDescendant(3L, 1L)).isTrue();
        assertThat(tree.isSelfOrDescendant(4L, 2L)).isFalse();
        assertThat(tree.isSelfOrDescendant(1L, 3L)).isFalse();
        assertThat(tree.isSelfOrDescendant(5L, 1L)).isFalse();
    }

    @Test
    void movingAWideSubtreeUnderADeepNodeWouldBreakTheLimit() {
        // Moving category 1 (height 3) under category 5 (depth 1) puts its deepest node at level 4.
        assertThat(tree.depth(5L) + tree.height(1L)).isGreaterThan(3);
        // Moving the leaf 3 (height 1) under 4 (depth 2) is fine.
        assertThat(tree.depth(4L) + tree.height(3L)).isLessThanOrEqualTo(3);
    }

    @Test
    void descendantsAndSelfListsEverythingBelow() {
        assertThat(tree.descendantsAndSelf(1L)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(tree.descendantsAndSelf(2L)).containsExactlyInAnyOrder(2L, 3L);
        assertThat(tree.descendantsAndSelf(5L)).containsExactly(5L);
    }
}