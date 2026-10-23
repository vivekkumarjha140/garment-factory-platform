package com.garmentfactory.shared.statemachine;

import com.garmentfactory.shared.exception.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The allowed status changes of one entity, declared once and checked everywhere. Anything not
 * listed is rejected, so a new status can never be reached by accident.
 */
public final class TransitionRules<S extends Enum<S>> {

    private final Map<S, Set<S>> allowed;

    private TransitionRules(Map<S, Set<S>> allowed) {
        this.allowed = allowed;
    }

    public static <S extends Enum<S>> Builder<S> forType(Class<S> type) {
        return new Builder<>(type);
    }

    public boolean canMove(S from, S to) {
        return allowed.getOrDefault(from, Set.of()).contains(to);
    }

    public void require(S from, S to, String what) {
        if (!canMove(from, to)) {
            throw ApiException.conflict("INVALID_TRANSITION", what + " cannot move from " + from + " to " + to + ".");
        }
    }

    public static final class Builder<S extends Enum<S>> {

        private final Map<S, Set<S>> map;

        private Builder(Class<S> type) {
            this.map = new EnumMap<>(type);
        }

        @SafeVarargs
        public final Builder<S> allow(S from, S... targets) {
            map.put(from, EnumSet.copyOf(List.of(targets)));
            return this;
        }

        public TransitionRules<S> build() {
            return new TransitionRules<>(Map.copyOf(map));
        }
    }
}