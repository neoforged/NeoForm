package net.neoforged.neoform.manifests;


import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public record Rule(
        RuleAction action,
        Map<String, Boolean> features,
        @Nullable OsCondition os
) {
    public Rule {
        Objects.requireNonNull(action);
        features = Objects.requireNonNullElseGet(features, Map::of);
    }

    public boolean evaluate() {
        return features.isEmpty() && (os == null || os.platformMatches());
    }

    public static boolean rulesMatch(Collection<Rule> rules) {
        if (rules.isEmpty()) {
            return true;
        }

        for (Rule rule : rules) {
            var ruleApplies = rule.evaluate();
            if (!ruleApplies && rule.action() == RuleAction.ALLOWED) {
                return false;
            } else if (ruleApplies && rule.action() == RuleAction.DISALLOWED) {
                return false;
            }
        }

        return true;
    }
}

