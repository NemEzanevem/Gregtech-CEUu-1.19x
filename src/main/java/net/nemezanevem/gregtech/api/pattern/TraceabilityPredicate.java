package net.nemezanevem.gregtech.api.pattern;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.nemezanevem.gregtech.GregTech;
import net.nemezanevem.gregtech.api.block.IHeatingCoilBlockStats;
import net.nemezanevem.gregtech.api.tileentity.MetaTileEntity;
import net.nemezanevem.gregtech.api.tileentity.interfaces.IGregTechTileEntity;
import net.nemezanevem.gregtech.api.util.BlockInfo;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TraceabilityPredicate {

    // Allow any block.
    public static TraceabilityPredicate ANY = new TraceabilityPredicate((state) -> true);
    // Allow the air block.
    public static TraceabilityPredicate AIR = new TraceabilityPredicate(blockWorldState -> blockWorldState.getBlockState().getBlock().isAir(blockWorldState.getBlockState(), blockWorldState.getWorld(), blockWorldState.getPos()));
    // Allow all heating coils, and require them to have the same type.
    public static Supplier<TraceabilityPredicate> HEATING_COILS = () -> new TraceabilityPredicate(blockWorldState -> {
        BlockState blockState = blockWorldState.getBlockState();
        if (GregTech.HEATING_COILS.containsKey(blockState)) {
            IHeatingCoilBlockStats stats = GregTech.HEATING_COILS.get(blockState);
            Object currentCoil = blockWorldState.getMatchContext().getOrPut("CoilType", stats);
            if (!currentCoil.equals(stats)) {
                blockWorldState.setError(new PatternStringError("gregtech.multiblock.pattern.error.coils"));
                return false;
            }
            blockWorldState.getMatchContext().getOrPut("VABlock", new LinkedList<>()).add(blockWorldState.getPos());
            return true;
        }
        return false;
    }, () -> GregTech.HEATING_COILS.entrySet().stream()
            // sort to make autogenerated jei previews not pick random coils each game load
            .sorted(Comparator.comparingInt(entry -> entry.getValue().getTier()))
            .map(entry -> new BlockInfo(entry.getKey(), null))
            .toArray(BlockInfo[]::new))
            .addTooltips("gregtech.multiblock.pattern.error.coils");

    public final List<SimplePredicate> common = new ArrayList<>();
    public final List<SimplePredicate> limited = new ArrayList<>();
    protected boolean isCenter;
    protected boolean hasAir = false;
    protected boolean isSingle = true;

    public TraceabilityPredicate() {
    }

    public TraceabilityPredicate(TraceabilityPredicate predicate) {
        common.addAll(predicate.common);
        limited.addAll(predicate.limited);
        isCenter = predicate.isCenter;
        hasAir = predicate.hasAir;
        isSingle = predicate.isSingle;
    }

    public TraceabilityPredicate(Predicate<BlockWorldState> predicate, Supplier<BlockInfo[]> candidates) {
        common.add(new SimplePredicate(predicate, candidates));
    }

    public TraceabilityPredicate(Predicate<BlockWorldState> predicate) {
        this(predicate, null);
    }

    public boolean isHasAir() {
        return hasAir;
    }

    public boolean isSingle() {
        return isSingle;
    }

    /**
     * Mark it as the controller of this multi. Normally you won't call it yourself. Use {@link MultiblockControllerBase#selfPredicate()} plz.
     */
    public TraceabilityPredicate setCenter() {
        isCenter = true;
        return this;
    }

    public TraceabilityPredicate sort() {
        limited.sort(Comparator.comparingInt(a -> ((a.minLayerCount + 1) * 100 + a.minGlobalCount)));
        return this;
    }

    /**
     * Add tooltips for candidates. They are shown in JEI Pages.
     * Do NOT pass {@link I18n#format(String, Object...)} calls here! Everything is will be translated when it's needed.
     * If you need parameters, use {@link #addTooltip(String, Object...)} instead.
     */
    public TraceabilityPredicate addTooltips(String... tips) {
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT && tips.length > 0) {
            List<String> tooltips = Arrays.stream(tips).collect(Collectors.toList());
            common.forEach(predicate -> {
                if (predicate.candidates == null) return;
                if (predicate.toolTips == null) {
                    predicate.toolTips = new ArrayList<>();
                }
                predicate.toolTips.addAll(tooltips);
            });
            limited.forEach(predicate -> {
                if (predicate.candidates == null) return;
                if (predicate.toolTips == null) {
                    predicate.toolTips = new ArrayList<>();
                }
                predicate.toolTips.addAll(tooltips);
            });
        }
        return this;
    }

    /**
     * Note: This method does not translate dynamically!! Parameters can not be updated once set.
     */
    public TraceabilityPredicate addTooltip(String langKey, Object... data) {
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            addTooltips(Component.translatable(langKey, data));
        }
        return this;
    }

    /**
     * Set the minimum number of candidate blocks.
     */
    public TraceabilityPredicate setMinGlobalLimited(int min) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.minGlobalCount = min;
        }
        return this;
    }

    public TraceabilityPredicate setMinGlobalLimited(int min, int previewCount) {
        return this.setMinGlobalLimited(min).setPreviewCount(previewCount);
    }

    /**
     * Set the maximum number of candidate blocks.
     */
    public TraceabilityPredicate setMaxGlobalLimited(int max) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.maxGlobalCount = max;
        }
        return this;
    }

    public TraceabilityPredicate setMaxGlobalLimited(int max, int previewCount) {
        return this.setMaxGlobalLimited(max).setPreviewCount(previewCount);
    }

    /**
     * Set the minimum number of candidate blocks for each aisle layer.
     */
    public TraceabilityPredicate setMinLayerLimited(int min) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.minLayerCount = min;
        }
        return this;
    }

    public TraceabilityPredicate setMinLayerLimited(int min, int previewCount) {
        return this.setMinLayerLimited(min).setPreviewCount(previewCount);
    }

    /**
     * Set the maximum number of candidate blocks for each aisle layer.
     */
    public TraceabilityPredicate setMaxLayerLimited(int max) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.maxLayerCount = max;
        }
        return this;
    }

    public TraceabilityPredicate setMaxLayerLimited(int max, int previewCount) {
        return this.setMaxLayerLimited(max).setPreviewCount(previewCount);
    }

    /**
     * Sets the Minimum and Maximum limit to the passed value
     *
     * @param limit The Maximum and Minimum limit
     */
    public TraceabilityPredicate setExactLimit(int limit) {
        return this.setMinGlobalLimited(limit).setMaxGlobalLimited(limit);
    }

    /**
     * Set the number of it appears in JEI pages. It only affects JEI preview. (The specific number)
     */
    public TraceabilityPredicate setPreviewCount(int count) {
        common.forEach(predicate -> predicate.previewCount = count);
        limited.forEach(predicate -> predicate.previewCount = count);
        return this;
    }

    public boolean test(BlockWorldState blockWorldState) {
        boolean flag = false;
        for (SimplePredicate predicate : limited) {
            if (predicate.testLimited(blockWorldState)) {
                flag = true;
            }
        }
        return flag || common.stream().anyMatch(predicate -> predicate.test(blockWorldState));
    }

    public TraceabilityPredicate or(TraceabilityPredicate other) {
        if (other != null) {
            TraceabilityPredicate newPredicate = new TraceabilityPredicate(this);
            if (this != AIR && other != AIR) {
                newPredicate.isSingle = false;
            } else {
                newPredicate.isSingle = this.isSingle && other.isSingle;
            }
            newPredicate.hasAir = newPredicate.hasAir || this == AIR || other == AIR;
            newPredicate.common.addAll(other.common);
            newPredicate.limited.addAll(other.limited);
            return newPredicate;
        }
        return this;
    }

    public static class SimplePredicate {
        public final Supplier<BlockInfo[]> candidates;

        public final Predicate<BlockWorldState> predicate;

        private List<Component> toolTips;

        public int minGlobalCount = -1;
        public int maxGlobalCount = -1;
        public int minLayerCount = -1;
        public int maxLayerCount = -1;

        public int previewCount = -1;

        public SimplePredicate(Predicate<BlockWorldState> predicate, Supplier<BlockInfo[]> candidates) {
            this.predicate = predicate;
            this.candidates = candidates;
        }

        public List<Component> getToolTips(TraceabilityPredicate predicates) {
            List<Component> result = new ArrayList<>();
            if (toolTips != null) {
                toolTips.forEach(tip -> result.add(Component.translatable(tip)));
            }
            if (minGlobalCount == maxGlobalCount && maxGlobalCount != -1) {
                result.add(Component.translatable("gregtech.multiblock.pattern.error.limited_exact", minGlobalCount));
            } else if (minGlobalCount != maxGlobalCount && minGlobalCount != -1 && maxGlobalCount != -1) {
                result.add(Component.translatable("gregtech.multiblock.pattern.error.limited_within", minGlobalCount, maxGlobalCount));
            } else {
                if (minGlobalCount != -1) {
                    result.add(Component.translatable("gregtech.multiblock.pattern.error.limited.1", minGlobalCount));
                }
                if (maxGlobalCount != -1) {
                    result.add(Component.translatable("gregtech.multiblock.pattern.error.limited.0", maxGlobalCount));
                }
            }
            if (minLayerCount != -1) {
                result.add(Component.translatable("gregtech.multiblock.pattern.error.limited.3", minLayerCount));
            }
            if (maxLayerCount != -1) {
                result.add(Component.translatable("gregtech.multiblock.pattern.error.limited.2", maxLayerCount));
            }
            if (predicates == null) return result;
            if (predicates.isSingle) {
                result.add(Component.translatable("gregtech.multiblock.pattern.single"));
            }
            if (predicates.hasAir) {
                result.add(Component.translatable("gregtech.multiblock.pattern.replaceable_air"));
            }
            return result;
        }

        public boolean test(BlockWorldState blockWorldState) {
            return predicate.test(blockWorldState);
        }

        public boolean testLimited(BlockWorldState blockWorldState) {
            return testGlobal(blockWorldState) && testLayer(blockWorldState);
        }

        public boolean testGlobal(BlockWorldState blockWorldState) {
            if (minGlobalCount == -1 && maxGlobalCount == -1) return true;
            Integer count = blockWorldState.globalCount.get(this);
            boolean base = predicate.test(blockWorldState);
            count = (count == null ? 0 : count) + (base ? 1 : 0);
            blockWorldState.globalCount.put(this, count);
            if (maxGlobalCount == -1 || count <= maxGlobalCount) return base;
            blockWorldState.setError(new SinglePredicateError(this, 0));
            return false;
        }

        public boolean testLayer(BlockWorldState blockWorldState) {
            if (minLayerCount == -1 && maxLayerCount == -1) return true;
            Integer count = blockWorldState.layerCount.get(this);
            boolean base = predicate.test(blockWorldState);
            count = (count == null ? 0 : count) + (base ? 1 : 0);
            blockWorldState.layerCount.put(this, count);
            if (maxLayerCount == -1 || count <= maxLayerCount) return base;
            blockWorldState.setError(new SinglePredicateError(this, 2));
            return false;
        }

        public List<ItemStack> getCandidates() {
            return candidates == null ? Collections.emptyList() : Arrays.stream(this.candidates.get()).filter(info -> info.getBlockState().getBlock() != Blocks.AIR).map(info -> {
                BlockState blockState = info.getBlockState();
                MetaTileEntity metaTileEntity = info.getTileEntity() instanceof IGregTechTileEntity ? ((IGregTechTileEntity) info.getTileEntity()).getMetaTileEntity() : null;
                if (metaTileEntity != null) {
                    return metaTileEntity.getStackForm();
                } else {
                    return new ItemStack(blockState.getBlock(), 1);
                }
            }).collect(Collectors.toList());
        }
    }

    public static class SinglePredicateError extends PatternError {
        public final SimplePredicate predicate;
        public final int type;

        public SinglePredicateError(SimplePredicate predicate, int type) {
            this.predicate = predicate;
            this.type = type;
        }

        @Override
        public List<List<ItemStack>> getCandidates() {
            return Collections.singletonList(predicate.getCandidates());
        }

        @Override
        public Component getErrorInfo() {
            int number = -1;
            if (type == 0) number = predicate.maxGlobalCount;
            if (type == 1) number = predicate.minGlobalCount;
            if (type == 2) number = predicate.maxLayerCount;
            if (type == 3) number = predicate.minLayerCount;
            return Component.translatable("gregtech.multiblock.pattern.error.limited." + type, number);
        }
    }

}
