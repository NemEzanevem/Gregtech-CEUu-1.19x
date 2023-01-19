package net.nemezanevem.gregtech.api.unification.material.info;

import net.minecraft.resources.ResourceLocation;
import net.nemezanevem.gregtech.GregTech;
import net.nemezanevem.gregtech.api.unification.material.Material;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class MaterialFlag {

    private static final Set<MaterialFlag> FLAG_REGISTRY = new HashSet<>();

    private final String name;

    private final Set<MaterialFlag> requiredFlags;
    private final Set<ResourceLocation> requiredProperties;

    private MaterialFlag(String name, Set<MaterialFlag> requiredFlags, Set<ResourceLocation> requiredProperties) {
        this.name = name;
        this.requiredFlags = requiredFlags;
        this.requiredProperties = requiredProperties;
        FLAG_REGISTRY.add(this);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MaterialFlag) {
            return ((MaterialFlag) o).name.equals(this.name);
        }
        return false;
    }

    public Set<MaterialFlag> verifyFlag(Material material) {
        requiredProperties.forEach(key -> {
            if (!material.hasProperty(key)) {
                GregTech.LOGGER.warn("Material {} does not have required property {} for flag {}!", material.getUnlocalizedName(), key.toString(), this.name);
            }
        });

        Set<MaterialFlag> thisAndDependencies = new HashSet<>(requiredFlags);
        thisAndDependencies.addAll(requiredFlags.stream()
                .map(f -> f.verifyFlag(material))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet()));

        return thisAndDependencies;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public static MaterialFlag getByName(String name) {
        return FLAG_REGISTRY.stream().filter(f -> f.toString().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public static class Builder {

        final String name;

        final Set<MaterialFlag> requiredFlags = new HashSet<>();
        final Set<ResourceLocation> requiredProperties = new HashSet<>();

        public Builder(String name) {
            this.name = name;
        }

        public Builder requireFlags(MaterialFlag... flags) {
            requiredFlags.addAll(Arrays.asList(flags));
            return this;
        }

        public Builder requireProps(ResourceLocation... propertyKeys) {
            requiredProperties.addAll(Arrays.asList(propertyKeys));
            return this;
        }

        public MaterialFlag build() {
            return new MaterialFlag(name, requiredFlags, requiredProperties);
        }
    }
}
