package net.nemezanevem.gregtech.api.capability;


import net.minecraft.world.item.crafting.RecipeType;

public interface IMultipleRecipeTypes {

    /**
     * Used to get all possible RecipeTypes a Multiblock can run
     * @return array of RecipeTypes
     */
    RecipeType<?>[] getAvailableRecipeTypes();

    /**
     *
     * @return the currently selected RecipeType
     */
    RecipeType<?> getCurrentRecipeType();
}
