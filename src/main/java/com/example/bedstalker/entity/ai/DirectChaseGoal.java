package com.example.bedstalker.entity.ai;

import com.example.bedstalker.entity.StalkerEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Plan B cuando no existe ruta (por ejemplo, el jugador esta tras una ventana de cristal o una
 * puerta de hierro): camina en linea recta hacia el jugador hasta chocar con el obstaculo.
 * {@link BreakBlocksGoal} se encarga entonces de romperlo.
 */
public class DirectChaseGoal extends Goal {
    private final StalkerEntity mob;
    private final double speed;
    private int checkDelay;
    private int attackCooldown;

    public DirectChaseGoal(StalkerEntity mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !mob.isAggressive() || mob.isCurled()) {
            return false;
        }
        if (mob.distanceToSqr(target) > 40.0D * 40.0D) {
            return false;
        }
        if (--checkDelay > 0) {
            return false;
        }
        checkDelay = 10 + mob.getRandom().nextInt(10);
        return mob.getNavigation().createPath(target, 0) == null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !mob.isAggressive() || mob.isCurled()) {
            return false;
        }
        if (--checkDelay <= 0) {
            checkDelay = 20;
            // Si ya hay ruta, cede el control al MeleeAttackGoal.
            return mob.getNavigation().createPath(target, 0) == null;
        }
        return true;
    }

    @Override
    public void start() {
        attackCooldown = 0;
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
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);

        if (attackCooldown > 0) {
            attackCooldown--;
        }
        double reach = (mob.getBbWidth() * 2.0F) * (mob.getBbWidth() * 2.0F) + target.getBbWidth();
        boolean verticalOk = Math.abs(target.getY() - mob.getY()) < 3.0D;
        if (attackCooldown <= 0 && verticalOk && mob.distanceToSqr(target.getX(), mob.getY(), target.getZ()) <= reach + 1.5D) {
            attackCooldown = 20;
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
        }
    }
}
