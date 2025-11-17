package net.neoforged.neoform.manifests;

import java.util.List;

public record MinecraftArguments(List<UnresolvedArgument> game, List<UnresolvedArgument> jvm) {
}
