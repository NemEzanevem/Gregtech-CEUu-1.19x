package net.nemezanevem.gregtech.api.capability.impl;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.nemezanevem.gregtech.api.capability.FeCompat;
import net.nemezanevem.gregtech.api.capability.GregtechCapabilities;
import net.nemezanevem.gregtech.api.capability.IEnergyContainer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.IEnergyStorage;
import net.nemezanevem.gregtech.api.GTValues;
import net.nemezanevem.gregtech.api.util.Util;

import javax.annotation.Nonnull;

public class EUToFEProvider extends CapabilityCompatProvider {

    /**
     * Internally used FE Buffer so that a very large packet of EU is not partially destroyed
     * on the conversion to FE. This is hidden from the player, but ensures that no energy
     * is ever lost on conversion, no matter the voltage tier or FE storage abilities.
     */
    private long feBuffer;

    public EUToFEProvider(BlockEntity tileEntity) {
        super(tileEntity);
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, Direction facing) {
        return ConfigHolder.compat.energy.nativeEUToFE &&
                capability == GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER &&
                hasUpvalueCapability(ForgeCapabilities.ENERGY, facing);
    }

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, Direction facing) {
        if (!ConfigHolder.compat.energy.nativeEUToFE || capability != GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER)
            return LazyOptional.empty();

        LazyOptional<IEnergyStorage> energyStorage = getUpvalueCapability(ForgeCapabilities.ENERGY, facing);
        return energyStorage != null ?
                LazyOptional.of(() -> new GTEnergyWrapper(energyStorage.resolve().get())).cast() :
                LazyOptional.empty();
    }

    public class GTEnergyWrapper implements IEnergyContainer {

        private final IEnergyStorage energyStorage;

        public GTEnergyWrapper(IEnergyStorage energyStorage) {
            this.energyStorage = energyStorage;
        }

        @Override
        public long acceptEnergyFromNetwork(Direction facing, long voltage, long amperage) {

            int receive = 0;

            // Try to use the internal buffer before consuming a new packet
            if (feBuffer > 0) {

                receive = energyStorage.receiveEnergy(safeCastLongToInt(feBuffer), true);

                if (receive == 0)
                    return 0;

                // Internal Buffer could provide the max RF the consumer could consume
                if (feBuffer > receive) {
                    feBuffer -= receive;
                    energyStorage.receiveEnergy(receive, false);
                    return 0;

                    // Buffer could not provide max value, save the remainder and continue processing
                } else {
                    receive = safeCastLongToInt(feBuffer);
                    feBuffer = 0;
                }
            }

            long maxPacket = FeCompat.toFe(voltage, FeCompat.ratio(false));
            long maximalValue = maxPacket * amperage;

            // Try to consume our remainder buffer plus a fresh packet
            if (receive != 0) {

                int consumable = energyStorage.receiveEnergy(safeCastLongToInt(maximalValue + receive), true);

                // Machine unable to consume any power
                if (consumable == 0)
                    return 0;

                // Only able to consume our buffered amount
                if (consumable == receive) {
                    energyStorage.receiveEnergy(consumable, false);
                    return 0;
                }

                // Able to consume our full packet as well as our remainder buffer
                if (consumable == maximalValue + receive) {
                    energyStorage.receiveEnergy(consumable, false);
                    return amperage;
                }

                int newPower = consumable - receive;

                // Able to consume buffered amount plus an even amount of packets (no buffer needed)
                if (newPower % maxPacket == 0) {
                    return energyStorage.receiveEnergy(consumable, false) / maxPacket;
                }

                // Able to consume buffered amount plus some amount of power with a packet remainder
                int ampsToConsume = safeCastLongToInt((newPower / maxPacket) + 1);
                feBuffer = safeCastLongToInt((maxPacket * ampsToConsume) - consumable);
                energyStorage.receiveEnergy(consumable, false);
                return ampsToConsume;

                // Else try to draw 1 full packet
            } else {

                int consumable = energyStorage.receiveEnergy(safeCastLongToInt(maximalValue), true);

                // Machine unable to consume any power
                if (consumable == 0)
                    return 0;

                // Able to accept the full amount of power
                if (consumable == maximalValue) {
                    energyStorage.receiveEnergy(consumable, false);
                    return amperage;
                }

                // Able to consume an even amount of packets
                if (consumable % maxPacket == 0) {
                    return energyStorage.receiveEnergy(consumable, false) / maxPacket;
                }

                // Able to consume power with some amount of power remainder in the packet
                int ampsToConsume = safeCastLongToInt((consumable / maxPacket) + 1);
                feBuffer = safeCastLongToInt((maxPacket * ampsToConsume) - consumable);
                energyStorage.receiveEnergy(consumable, false);
                return ampsToConsume;
            }
        }

        @Override
        public long changeEnergy(long delta) {
            if (delta == 0) return 0;
            else if (delta < 0) return FeCompat.extractEu(energyStorage, -delta);
            else return FeCompat.insertEu(energyStorage, delta);
        }

        @Override
        public long getEnergyCapacity() {
            return FeCompat.toEu(energyStorage.getMaxEnergyStored(), FeCompat.ratio(false));
        }

        @Override
        public long getEnergyStored() {
            return FeCompat.toEu(energyStorage.getEnergyStored(), FeCompat.ratio(false));
        }

        /**
         * Most RF/FE cables blindly try to insert energy without checking if there is space, since the receiving IEnergyStorage should handle it.
         * This simulates that behavior in most places by allowing our "is there space" checks to pass and letting the cable attempt to insert energy.
         * If the wrapped TE actually cannot accept any more energy, the energy transfer will return 0 before any changes to our internal rf buffer.
         */
        @Override
        public long getEnergyCanBeInserted() {
            return Math.max(1, getEnergyCapacity() - getEnergyStored());
        }

        @Override
        public long getInputAmperage() {
            return getInputVoltage() == 0 ? 0 : 2;
        }

        @Override
        public long getInputVoltage() {
            long maxInput = energyStorage.receiveEnergy(Integer.MAX_VALUE, true);

            if (maxInput == 0) return 0;
            return GTValues.V[Util.getTierByVoltage(FeCompat.toEu(maxInput, FeCompat.ratio(false)))];
        }

        @Override
        public boolean inputsEnergy(Direction facing) {
            return energyStorage.canReceive();
        }

        /**
         * Wrapped FE-consumers should not be able to output EU.
         */
        @Override
        public boolean outputsEnergy(Direction facing) {
            return false;
        }

        /**
         * Hide this BlockEntity EU-capability in TOP. Allows FE-machines to
         * "silently" accept EU without showing their charge in EU in TOP.
         * Let the machine display it in FE instead, however it chooses to.
         */
        @Override
        public boolean isOneProbeHidden() {
            return true;
        }
    }

    /**
     * Safely cast a Long to an Int without overflow.
     *
     * @param v The Long value to cast to an Int.
     * @return v, casted to Int, or Integer.MAX_VALUE if it would overflow.
     */
    public static int safeCastLongToInt(long v) {
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
    }
}
