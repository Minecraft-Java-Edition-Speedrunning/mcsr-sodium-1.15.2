package me.jellysquid.mods.sodium.mixin.features.world_ticking;

import me.jellysquid.mods.sodium.client.util.rand.XoRoShiRoRandom;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.level.LevelProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;
import java.util.function.BiFunction;

@Mixin(ClientWorld.class)
public abstract class MixinClientWorld extends World {
    @Shadow
    protected abstract void addParticle(BlockPos pos, BlockState state, ParticleEffect parameters, boolean bl);

    protected MixinClientWorld(LevelProperties levelProperties, DimensionType dimensionType,
                               BiFunction<World, Dimension, ChunkManager> chunkManagerProvider, Profiler profiler,
                               boolean isClient) {
        super(levelProperties, dimensionType, chunkManagerProvider, profiler, isClient);
    }

    @Redirect(method = "doRandomBlockDisplayTicks", at = @At(value = "NEW", target = "java/util/Random"))
    private Random redirectRandomTickRandom() {
        return new XoRoShiRoRandom();
    }

    /**
     * @reason Avoid allocations, branch code out, early-skip some code
     * @author JellySquid
     */
    @Overwrite
    public void randomBlockDisplayTick(int xCenter, int yCenter, int zCenter, int radius, Random random, boolean spawnBarrierParticles, BlockPos.Mutable pos) {
        int x = xCenter + (random.nextInt(radius) - random.nextInt(radius));
        int y = yCenter + (random.nextInt(radius) - random.nextInt(radius));
        int z = zCenter + (random.nextInt(radius) - random.nextInt(radius));

        pos.set(x, y, z);

        BlockState blockState = this.getBlockState(pos);

        if (!blockState.isAir()) {
            this.performBlockDisplayTick(blockState, pos, random, spawnBarrierParticles);
        }

        FluidState fluidState = blockState.getFluidState();

        if (!fluidState.isEmpty()) {
            this.performFluidDisplayTick(blockState, fluidState, pos, random);
        }
    }

    private void performBlockDisplayTick(BlockState blockState, BlockPos pos, Random random, boolean spawnBarrierParticles) {
        blockState.getBlock().randomDisplayTick(blockState, this, pos, random);

        if (spawnBarrierParticles && blockState.getBlock() == Blocks.BARRIER) {
            this.performBarrierDisplayTick(pos);
        }
    }

    private void performBarrierDisplayTick(BlockPos pos) {
        this.addParticle(ParticleTypes.BARRIER, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                0.0D, 0.0D, 0.0D);
    }

    private void performFluidDisplayTick(BlockState blockState, FluidState fluidState, BlockPos pos, Random random) {
        fluidState.randomDisplayTick(this, pos, random);

        ParticleEffect particleEffect = fluidState.getParticle();

        if (particleEffect != null && random.nextInt(10) == 0) {
            boolean solid = blockState.isSideSolidFullSquare(this, pos, Direction.DOWN);

            // FIXME: don't allocate here
            BlockPos blockPos = pos.down();
            this.addParticle(blockPos, this.getBlockState(blockPos), particleEffect, solid);
        }
    }
}
