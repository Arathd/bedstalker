package com.example.bedstalker.entity.ai;

import com.example.bedstalker.config.StalkerConfig;
import com.example.bedstalker.entity.StalkerEntity;
import com.example.bedstalker.entity.StalkerEntity.BedState;
import com.example.bedstalker.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Ciclo completo "esconderse bajo la cama":
 * APPROACH (camina hasta la cama) -> ENTER (animacion de agacharse y deslizarse debajo) ->
 * HIDDEN (espera inmovil, casi oculto por el colchon) -> EXIT (sale y ataca por sorpresa).
 *
 * El estado visible (ENTERING / HIDDEN / EXITING) se guarda en el SynchedEntityData del mob,
 * de modo que cliente y servidor muestran la misma animacion y hitbox.
 */
public class HideUnderBedGoal extends Goal {
    private enum Phase { APPROACH, ENTER, HIDDEN, EXIT }

    private static final int MIN_HIDDEN_TICKS = 40;
    private static final int MAX_APPROACH_TICKS = 400;

    private final StalkerEntity mob;

    private Phase phase = Phase.APPROACH;
    private BlockPos headPos;
    private Vec3 center;
    private Direction facing = Direction.NORTH;
    private int timer;
    private int approachTicks;
    private int exitRetryDelay;
    private Vec3 startPos = Vec3.ZERO;
    private Vec3 exitPos;

    public HideUnderBedGoal(StalkerEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    // ------------------------------------------------------------------ seleccion

    @Override
    public boolean canUse() {
        // Reanudar tras cargar un mundo con el Stalker ya escondido.
        if (mob.isCurled()) {
            return true;
        }
        if (!StalkerConfig.HIDE_UNDER_BEDS.get()) {
            return false;
        }
        if (mob.getHideCooldown() > 0 || mob.isAggressive()) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (mob.getRandom().nextInt(reducedTickDelay(50)) != 0) {
            return false;
        }
        double dist = mob.distanceTo(target);
        if (dist < 6.0D || dist > 40.0D) {
            return false;
        }
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        Optional<BlockPos> found = serverLevel.getPoiManager().findClosest(
                holder -> holder.is(PoiTypes.HOME), mob.blockPosition(), 16, PoiManager.Occupancy.ANY);
        if (found.isEmpty()) {
            return false;
        }
        BlockPos pos = found.get();
        BlockState state = serverLevel.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) {
            return false;
        }
        // La cama debe estar razonablemente cerca del jugador para emboscarlo.
        if (target.distanceToSqr(Vec3.atCenterOf(pos)) > 28.0D * 28.0D) {
            return false;
        }

        Direction dir = state.getValue(BedBlock.FACING);
        Vec3 bedCenter = bedCenter(pos, dir);
        if (mob.findExitPos(bedCenter) == null) {
            return false; // no habria hueco para salir
        }
        Path path = mob.getNavigation().createPath(pos, 1);
        if (path == null) {
            return false;
        }

        this.headPos = pos;
        this.facing = dir;
        this.center = bedCenter;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.isCurled()) {
            return true;
        }
        // Solo la fase de aproximacion puede abortarse.
        return phase == Phase.APPROACH
                && !mob.isAggressive()
                && mob.getTarget() != null
                && approachTicks < MAX_APPROACH_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        timer = 0;
        approachTicks = 0;
        exitRetryDelay = 0;
        exitPos = null;

        if (mob.isCurled()) {
            // Reanudacion desde NBT.
            phase = Phase.HIDDEN;
            BlockPos saved = mob.getHiddenBedPos();
            if (saved != null) {
                BlockState state = mob.level().getBlockState(saved);
                if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.FACING)) {
                    headPos = saved;
                    facing = state.getValue(BedBlock.FACING);
                    center = bedCenter(saved, facing);
                }
            }
            if (center == null) {
                center = mob.position();
                headPos = null;
            }
            mob.setBedState(BedState.HIDDEN);
            return;
        }

        phase = Phase.APPROACH;
        mob.getNavigation().moveTo(center.x, center.y, center.z, 0.9D);
    }

    @Override
    public void stop() {
        if (mob.isCurled()) {
            // Salida de emergencia (el goal fue detenido a la fuerza).
            Vec3 out = center != null ? mob.findExitPos(center) : null;
            if (out != null) {
                mob.setPos(out.x, out.y, out.z);
            }
            mob.releaseFromBed();
        } else if (phase == Phase.APPROACH) {
            mob.setHideCooldown(200);
            mob.getNavigation().stop();
        }
        phase = Phase.APPROACH;
        headPos = null;
        center = null;
        exitPos = null;
    }

    // ------------------------------------------------------------------ ciclo

    @Override
    public void tick() {
        switch (phase) {
            case APPROACH -> tickApproach();
            case ENTER -> tickEnter();
            case HIDDEN -> tickHidden();
            case EXIT -> tickExit();
        }
    }

    private void tickApproach() {
        approachTicks++;
        mob.getLookControl().setLookAt(center.x, center.y + 0.5D, center.z);

        if (approachTicks % 20 == 0) {
            mob.getNavigation().moveTo(center.x, center.y, center.z, 0.9D);
        }
        double dx = center.x - mob.getX();
        double dz = center.z - mob.getZ();
        if (dx * dx + dz * dz < 2.3D * 2.3D && Math.abs(center.y - mob.getY()) < 1.5D) {
            beginEnter();
        }
    }

    private void beginEnter() {
        phase = Phase.ENTER;
        timer = 0;
        startPos = mob.position();
        mob.getNavigation().stop();
        mob.setHiddenBedPos(headPos);
        mob.setBedState(BedState.ENTERING);
        faceBedAxis();
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.HIDE.get(),
                SoundSource.HOSTILE, 1.4F, 0.9F + mob.getRandom().nextFloat() * 0.15F);
    }

    private void tickEnter() {
        timer++;
        faceBedAxis();

        // Primero se agacha (0-35 %), luego se desliza bajo la cama.
        float t = timer / (float) StalkerEntity.ENTER_TICKS;
        float k = Mth.clamp((t - 0.35F) / 0.65F, 0.0F, 1.0F);
        k = k * k * (3.0F - 2.0F * k);
        Vec3 p = startPos.lerp(center, k);
        mob.setPos(p.x, p.y, p.z);
        mob.setDeltaMovement(Vec3.ZERO);

        if (timer >= StalkerEntity.ENTER_TICKS) {
            mob.setPos(center.x, center.y, center.z);
            phase = Phase.HIDDEN;
            timer = 0;
            mob.setBedState(BedState.HIDDEN);
        }
    }

    private void tickHidden() {
        timer++;
        mob.setPos(center.x, center.y, center.z);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.getNavigation().stop();
        faceBedAxis();

        if (exitRetryDelay > 0) {
            exitRetryDelay--;
            return;
        }
        if (timer % 5 != 0) {
            return;
        }
        if (shouldWake()) {
            beginExit();
        }
    }

    private boolean shouldWake() {
        if (mob.consumeWakeRequest()) {
            return true;
        }
        Level level = mob.level();
        if (headPos != null) {
            BlockState state = level.getBlockState(headPos);
            if (!(state.getBlock() instanceof BedBlock)) {
                return true; // le quitaron la cama
            }
        }
        if (timer < MIN_HIDDEN_TICKS) {
            return false;
        }
        // Jugador que se acerca demasiado.
        Player near = level.getNearestPlayer(
                TargetingConditions.forCombat().ignoreLineOfSight().range(3.5D), mob);
        if (near != null) {
            return true;
        }
        // Jugador durmiendo cerca: la emboscada perfecta.
        for (Player p : level.players()) {
            if (p.isSleeping() && !p.isSpectator() && p.distanceToSqr(mob) < 7.0D * 7.0D) {
                return true;
            }
        }
        return false;
    }

    private void beginExit() {
        Vec3 out = mob.findExitPos(center);
        if (out == null) {
            exitRetryDelay = 40; // sin hueco libre: reintenta mas tarde
            return;
        }
        exitPos = out;
        phase = Phase.EXIT;
        timer = 0;
        mob.setBedState(BedState.EXITING);
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.EMERGE.get(),
                SoundSource.HOSTILE, 1.8F, 0.85F + mob.getRandom().nextFloat() * 0.15F);
    }

    private void tickExit() {
        timer++;
        faceBedAxis();
        float k = Mth.clamp(timer / (float) StalkerEntity.EXIT_TICKS, 0.0F, 1.0F);
        k = k * k * (3.0F - 2.0F * k);
        Vec3 p = center.lerp(exitPos, k);
        mob.setPos(p.x, p.y, p.z);
        mob.setDeltaMovement(Vec3.ZERO);

        if (timer >= StalkerEntity.EXIT_TICKS) {
            // Primero la posicion, despues el estado (restaura fisica y hitbox completa).
            mob.setPos(exitPos.x, exitPos.y, exitPos.z);
            mob.setHiddenBedPos(null);
            mob.setBedState(BedState.NONE);
            mob.setHideCooldown(1200 + mob.getRandom().nextInt(1200));
            mob.triggerAggression(240); // ataque por sorpresa
        }
    }

    // ------------------------------------------------------------------ utilidades

    private void faceBedAxis() {
        float yaw = facing.toYRot();
        mob.setYRot(yaw);
        mob.yRotO = yaw;
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
    }

    /** Centro del colchon completo (punto medio entre las dos mitades de la cama), a nivel del suelo. */
    private static Vec3 bedCenter(BlockPos headPos, Direction facing) {
        BlockPos foot = headPos.relative(facing.getOpposite());
        return new Vec3(
                (headPos.getX() + foot.getX()) / 2.0D + 0.5D,
                headPos.getY(),
                (headPos.getZ() + foot.getZ()) / 2.0D + 0.5D);
    }
}
