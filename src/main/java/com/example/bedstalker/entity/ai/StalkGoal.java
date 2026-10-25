package com.example.bedstalker.entity.ai;

import com.example.bedstalker.entity.StalkerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Acecho: se aproxima lentamente al objetivo, intentando colocarse a su espalda.
 * Si el jugador lo mira, se queda completamente quieto (y tras unos instantes se vuelve agresivo).
 */
public class StalkGoal extends Goal {
    private final StalkerEntity mob;
    private final double speed;
    private int repathDelay;

    public StalkGoal(StalkerEntity mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && !mob.isAggressive() && !mob.isCurled();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repathDelay = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }

        // Congelado mientras el jugador lo mira.
        if (target instanceof Player player && mob.isLookedAtBy(player)) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
            return;
        }

        mob.getLookControl().setLookAt(target, 10.0F, 10.0F);

        if (--repathDelay <= 0) {
            repathDelay = 10 + mob.getRandom().nextInt(10);
            Vec3 goal = approachPoint(target);
            boolean ok = mob.getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
            if (!ok) {
                mob.getNavigation().moveTo(target, speed);
            }
        }
    }

    /** Lejos: directo al objetivo. Cerca: un punto detras de la espalda del jugador. */
    private Vec3 approachPoint(LivingEntity target) {
        if (mob.distanceToSqr(target) > 14.0D * 14.0D) {
            return target.position();
        }
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            return target.position();
        }
        return target.position().subtract(flat.normalize().scale(2.5D));
    }
}
