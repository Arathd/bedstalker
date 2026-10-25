package com.example.bedstalker.entity.ai;

import com.example.bedstalker.config.StalkerConfig;
import com.example.bedstalker.entity.StalkerEntity;
import com.example.bedstalker.registry.ModSounds;
import com.example.bedstalker.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Rompe puertas de madera, puertas de hierro (mas lento) y cristales / paneles que estorban
 * en direccion al objetivo.
 *
 * Sonidos: al romper se usa {@link Level#destroyBlock}, que emite el sonido y las particulas
 * normales de rotura de ese bloque (evento 2001), mas el sonido grave propio {@link ModSounds#SMASH}.
 */
public class BreakBlocksGoal extends Goal {
    private final StalkerEntity mob;

    @Nullable
    private BlockPos targetPos;
    private int progress;
    private int requiredTicks;
    private int lastStage = -1;
    private int scanDelay;

    public BreakBlocksGoal(StalkerEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    // ------------------------------------------------------------------ clasificacion

    private enum Kind { NONE, WOOD_DOOR, IRON_DOOR, WINDOW }

    private static Kind classify(BlockState state) {
        if (state.isAir()) {
            return Kind.NONE;
        }
        if (state.getBlock() instanceof DoorBlock) {
            if (state.getValue(DoorBlock.OPEN)) {
                return Kind.NONE; // una puerta abierta no estorba
            }
            if (StalkerConfig.BREAK_WOODEN_DOORS.get() && state.is(ModTags.BREAKABLE_WOODEN_DOORS)) {
                return Kind.WOOD_DOOR;
            }
            if (StalkerConfig.BREAK_IRON_DOORS.get() && state.is(ModTags.BREAKABLE_IRON_DOORS)) {
                return Kind.IRON_DOOR;
            }
            return Kind.NONE;
        }
        if (StalkerConfig.BREAK_WINDOWS.get() && state.is(ModTags.BREAKABLE_WINDOWS)) {
            return Kind.WINDOW;
        }
        return Kind.NONE;
    }

    private static int ticksFor(Kind kind) {
        return switch (kind) {
            case WOOD_DOOR -> StalkerConfig.WOODEN_DOOR_TICKS.get();
            case IRON_DOOR -> StalkerConfig.IRON_DOOR_TICKS.get();
            case WINDOW -> StalkerConfig.WINDOW_TICKS.get();
            default -> Integer.MAX_VALUE;
        };
    }

    // ------------------------------------------------------------------ goal

    @Override
    public boolean canUse() {
        if (mob.isCurled()) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || mob.distanceToSqr(target) > 40.0D * 40.0D) {
            return false;
        }
        if (!ForgeEventFactory.getMobGriefingEvent(mob.level(), mob)) {
            return false;
        }
        if (--scanDelay > 0) {
            return false;
        }
        scanDelay = 4;
        targetPos = findBlocker(target);
        return targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetPos == null || mob.isCurled() || mob.getTarget() == null) {
            return false;
        }
        if (!ForgeEventFactory.getMobGriefingEvent(mob.level(), mob)) {
            return false;
        }
        if (mob.distanceToSqr(Vec3.atCenterOf(targetPos)) > 3.0D * 3.0D) {
            return false;
        }
        return classify(mob.level().getBlockState(targetPos)) != Kind.NONE;
    }

    @Override
    public void start() {
        progress = 0;
        lastStage = -1;
        BlockState state = mob.level().getBlockState(targetPos);
        requiredTicks = ticksFor(classify(state));
        mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        if (targetPos != null) {
            mob.level().destroyBlockProgress(mob.getId(), targetPos, -1);
        }
        targetPos = null;
        progress = 0;
        lastStage = -1;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (targetPos == null) {
            return;
        }
        Level level = mob.level();
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(Vec3.atCenterOf(targetPos));

        // Golpe sordo repetido + animacion de ataque mientras trabaja la puerta / el cristal.
        if (progress % 20 == 0) {
            level.playSound(null, targetPos, ModSounds.BANG.get(), SoundSource.HOSTILE,
                    1.6F, 0.7F + mob.getRandom().nextFloat() * 0.2F);
            mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            mob.triggerAnim("attack_controller", "attack");
        }

        progress++;
        int stage = (int) ((float) progress / (float) requiredTicks * 10.0F);
        if (stage != lastStage && stage < 10) {
            level.destroyBlockProgress(mob.getId(), targetPos, stage);
            lastStage = stage;
        }

        if (progress >= requiredTicks) {
            BlockState state = level.getBlockState(targetPos);
            Kind kind = classify(state);
            if (kind != Kind.NONE) {
                boolean drop = kind != Kind.WINDOW;
                // Sonido y particulas normales de rotura del bloque.
                level.destroyBlock(targetPos, drop, mob);
                // Sonido adicional grave e inquietante.
                level.playSound(null, targetPos, ModSounds.SMASH.get(), SoundSource.HOSTILE,
                        2.2F, 0.75F + mob.getRandom().nextFloat() * 0.15F);
            }
            stop();
        }
    }

    // ------------------------------------------------------------------ busqueda

    /** Busca un bloque rompible justo delante del mob, en direccion al siguiente nodo del camino / al objetivo. */
    @Nullable
    private BlockPos findBlocker(LivingEntity target) {
        Vec3 dir = new Vec3(target.getX() - mob.getX(), 0.0D, target.getZ() - mob.getZ());

        Path path = mob.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            Node next = path.getNextNode();
            Vec3 toNode = new Vec3(next.x + 0.5D - mob.getX(), 0.0D, next.z + 0.5D - mob.getZ());
            if (toNode.lengthSqr() > 0.04D) {
                dir = toNode;
            }
        }
        if (dir.lengthSqr() < 1.0E-4D) {
            return null;
        }
        dir = dir.normalize();

        double[] distances = {0.9D, 1.5D};
        for (double dist : distances) {
            for (int dy = 0; dy < 3; dy++) {
                BlockPos pos = BlockPos.containing(
                        mob.getX() + dir.x * dist,
                        mob.getY() + dy + 0.1D,
                        mob.getZ() + dir.z * dist);
                if (classify(mob.level().getBlockState(pos)) != Kind.NONE) {
                    return pos;
                }
            }
        }
        return null;
    }
}
