package com.example.bedstalker.entity.ai;

import com.example.bedstalker.entity.StalkerEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Ataque cuerpo a cuerpo, solo cuando el Stalker esta en modo agresivo. */
public class StalkerMeleeGoal extends MeleeAttackGoal {
    private final StalkerEntity stalker;

    public StalkerMeleeGoal(StalkerEntity mob, double speedModifier) {
        super(mob, speedModifier, false);
        this.stalker = mob;
    }

    @Override
    public boolean canUse() {
        return stalker.isAggressive() && !stalker.isCurled() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return stalker.isAggressive() && !stalker.isCurled() && super.canContinueToUse();
    }
}
