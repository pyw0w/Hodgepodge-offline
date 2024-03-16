package com.mitchej123.hodgepodge.mixins.early.minecraft.fastload;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mitchej123.hodgepodge.mixins.interfaces.FastWorldServer;
import com.mitchej123.hodgepodge.server.FastCPS;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.List;

@Mixin(PlayerManager.class)
public class MixinPlayerManager {

    @Shadow @Final private WorldServer theWorldServer;

    private static final ChunkCoordIntPair hodgepodge$dummyCoord = new ChunkCoordIntPair(Integer.MAX_VALUE, Integer.MAX_VALUE);

    @WrapOperation(
            method = "filterChunkLoadQueue",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/management/PlayerManager;getOrCreateChunkWatcher(IIZ)Lnet/minecraft/server/management/PlayerManager$PlayerInstance;",
                    ordinal = 1))
    private PlayerManager.PlayerInstance hodgepodge$throttleChunkGen(PlayerManager instance, int cx, int cz, boolean instantiate, Operation<PlayerManager.PlayerInstance> original) {

        final long key = ChunkCoordIntPair.chunkXZ2Int(cx, cz);

        if (((FastCPS) this.theWorldServer.theChunkProviderServer).doesChunkExist(cx, cz, key))
            return original.call(instance, cx, cz, instantiate); // Always load generated chunks
        if (((FastWorldServer) this.theWorldServer).isThrottlingGen())
            return null; // Don't generate new chunks while throttling

        // Generate, but count it against the budget this tick
        ((FastWorldServer) this.theWorldServer).spendGenBudget(1);
        return original.call(instance, cx, cz, instantiate);
    }

    @WrapOperation(
            method = "filterChunkLoadQueue",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/management/PlayerManager$PlayerInstance;chunkLocation:Lnet/minecraft/world/ChunkCoordIntPair;",
                    ordinal = 1))
    private ChunkCoordIntPair hodgepodge$throttleChunkGen(PlayerManager.PlayerInstance instance, Operation<ChunkCoordIntPair> original) {
        if (instance != null) return original.call(instance);
        return hodgepodge$dummyCoord;
    }

    @WrapWithCondition(method = "updatePlayerPertinentChunks", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean hodgepodge$throttleChunkGen(
            List<ChunkCoordIntPair> instance,
            @Coerce Object coord,
            @Local(name = "i") int pcx,
            @Local(name = "j") int pcz) {

        final ChunkCoordIntPair c = (ChunkCoordIntPair) coord;
        final int cx = c.chunkXPos;
        final int cz = c.chunkZPos;
        if (cx == pcx && cz == pcz)
            return true; // Always load the player's chunk
        if (((FastCPS) this.theWorldServer.theChunkProviderServer).doesChunkExist(c))
            return true; // Always load generated chunks
        if (((FastWorldServer) this.theWorldServer).isThrottlingGen())
            return false; // Don't generate new chunks while throttling

        // Generate, but count it against the budget this tick
        ((FastWorldServer) this.theWorldServer).spendGenBudget(1);
        return true;
    }
}
