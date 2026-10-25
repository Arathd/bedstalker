package com.example.bedstalker.entity;

import com.example.bedstalker.entity.ai.BreakBlocksGoal;
import com.example.bedstalker.entity.ai.DirectChaseGoal;
import com.example.bedstalker.entity.ai.HideUnderBedGoal;
import com.example.bedstalker.entity.ai.StalkGoal;
import com.example.bedstalker.entity.ai.StalkerMeleeGoal;
import com.example.bedstalker.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * Stalker: mob hostil alto (2.9 bloques) que acecha al jugador, se esconde bajo las camas,
 * rompe puertas / ventanas y ataca por sorpresa.
 */
public class StalkerEntity extends Monster implements GeoEntity {

    /** Estado "bajo la cama". Sincronizado cliente/servidor para animacion y hitbox. */
    public enum BedState {
        NONE, ENTERING, HIDDEN, EXITING;

        public static BedState byId(int id) {
            BedState[] values = values();
            return values[Mth.clamp(id, 0, values.length - 1)];
        }
    }

    // Duraciones (ticks). Deben coincidir con la longitud de las animaciones enter_bed / exit_bed.
    public static final int ENTER_TICKS = 30;
    public static final int EXIT_TICKS = 20;

    private static final int STARE_TICKS_TO_AGGRO = 12;
    private static final double LOOK_THRESHOLD = 0.80D;
    private static final double AGGRO_CLOSE_DISTANCE = 3.5D;

    private static final EntityDataAccessor<Integer> DATA_BED_STATE =
            SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.INT);

    private static final EntityDimensions CURLED_DIMENSIONS = EntityDimensions.fixed(0.8F, 0.35F);

    // ---- Animaciones (nombres definidos en stalker.animation.json) ----
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.stalker.idle");
    private static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("animation.stalker.walk");
    private static final RawAnimation ANIM_RUN = RawAnimation.begin().thenLoop("animation.stalker.run");
    private static final RawAnimation ANIM_ENTER_BED = RawAnimation.begin().thenPlayAndHold("animation.stalker.enter_bed");
    private static final RawAnimation ANIM_HIDDEN = RawAnimation.begin().thenLoop("animation.stalker.hidden");
    private static final RawAnimation ANIM_EXIT_BED = RawAnimation.begin().thenPlay("animation.stalker.exit_bed");
    private static final RawAnimation ANIM_ATTACK = RawAnimation.begin().thenPlay("animation.stalker.attack");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private BlockPos hiddenBedPos;
    private boolean wakeRequested;
    private int hideCooldown = 300;
    private int lookedAtTicks;
    private int angerTicks;
    private int proximitySoundTimer = 40;
    private int stepCounter;

    public StalkerEntity(EntityType<? extends StalkerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 30;
        this.setMaxUpStep(1.0F);
        if (this.getNavigation() instanceof GroundPathNavigation ground) {
            // Permite que el pathfinding atraviese puertas de madera cerradas (luego las rompe).
            ground.setCanOpenDoors(true);
        }
    }

    // ------------------------------------------------------------------ atributos / spawn

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.6D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    /**
     * Spawn raro: debe estar oscuro y ademas (a) ser de noche con baja probabilidad o
     * (b) haber una cama (casa) a menos de 48 bloques.
     */
    public static boolean checkStalkerSpawnRules(EntityType<StalkerEntity> type, ServerLevelAccessor level,
                                                 MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (reason == MobSpawnType.SPAWNER) {
            return true;
        }
        if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) {
            return false;
        }
        if (reason != MobSpawnType.NATURAL) {
            return true;
        }
        ServerLevel serverLevel = level.getLevel();
        long time = serverLevel.getDayTime() % 24000L;
        boolean night = time >= 13000L && time <= 23000L;
        boolean nearHome = serverLevel.getPoiManager()
                .getCountInRange(holder -> holder.is(PoiTypes.HOME), pos, 48, PoiManager.Occupancy.ANY) > 0;
        if (nearHome) {
            return random.nextInt(2) == 0;
        }
        return night && random.nextInt(4) == 0;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    // ------------------------------------------------------------------ IA

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HideUnderBedGoal(this));
        this.goalSelector.addGoal(2, new BreakBlocksGoal(this));
        this.goalSelector.addGoal(3, new StalkerMeleeGoal(this, 1.15D));
        this.goalSelector.addGoal(4, new DirectChaseGoal(this, 1.1D));
        this.goalSelector.addGoal(5, new StalkGoal(this, 0.55D));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (hideCooldown > 0) {
            hideCooldown--;
        }

        LivingEntity target = getTarget();
        Player player = target instanceof Player p ? p : null;

        if (!isCurled()) {
            updateAggression(player);
        }
        tickProximitySounds();
    }

    private void updateAggression(@Nullable Player player) {
        if (player == null || !player.isAlive()) {
            lookedAtTicks = 0;
            if (angerTicks > 0 && --angerTicks == 0) {
                setAggressive(false);
            }
            return;
        }

        double distSqr = distanceToSqr(player);
        boolean looked = distSqr < 32.0D * 32.0D && isLookedAtBy(player);

        if (looked) {
            lookedAtTicks = Math.min(lookedAtTicks + 1, 40);
        } else if (lookedAtTicks > 0) {
            lookedAtTicks--;
        }

        boolean tooClose = distSqr < AGGRO_CLOSE_DISTANCE * AGGRO_CLOSE_DISTANCE;
        if (lookedAtTicks >= STARE_TICKS_TO_AGGRO || tooClose) {
            triggerAggression(140);
        } else if (angerTicks > 0) {
            angerTicks--;
            if (angerTicks == 0) {
                setAggressive(false);
                lookedAtTicks = 0;
            }
        }
    }

    /** Pasa a modo agresivo (o lo prolonga). */
    public void triggerAggression(int ticks) {
        this.angerTicks = Math.max(this.angerTicks, ticks);
        if (!isAggressive()) {
            setAggressive(true);
            level().playSound(null, getX(), getY(), getZ(), ModSounds.SCREAM.get(), SoundSource.HOSTILE,
                    2.0F, 0.85F + random.nextFloat() * 0.2F);
        }
    }

    /** El jugador mira al Stalker (cono + linea de vision). */
    public boolean isLookedAtBy(Player player) {
        Vec3 view = player.getViewVector(1.0F).normalize();
        Vec3 toMe = new Vec3(getX() - player.getX(), getEyeY() - player.getEyeY(), getZ() - player.getZ());
        double dist = toMe.length();
        if (dist < 1.0E-4D) {
            return true;
        }
        toMe = toMe.scale(1.0D / dist);
        return view.dot(toMe) > LOOK_THRESHOLD && player.hasLineOfSight(this);
    }

    /** Respiracion / susurros cuando hay un jugador cerca pero no lo esta viendo. */
    private void tickProximitySounds() {
        if (--proximitySoundTimer > 0) {
            return;
        }
        proximitySoundTimer = 60 + random.nextInt(100);

        Player nearest = level().getNearestPlayer(this, 16.0D);
        if (nearest == null || nearest.isSpectator()) {
            return;
        }
        if (!isCurled() && isLookedAtBy(nearest)) {
            return; // visible: sin susurros
        }
        float closeness = 1.0F - (float) (distanceTo(nearest) / 16.0D);
        float volume = (0.35F + 0.9F * closeness) * (isCurled() ? 0.7F : 1.0F);
        SoundEvent sound = random.nextInt(3) == 0 ? ModSounds.WHISPER.get() : ModSounds.BREATH.get();
        level().playSound(null, getX(), getEyeY(), getZ(), sound, SoundSource.HOSTILE,
                volume, 0.85F + random.nextFloat() * 0.3F);
    }

    // ------------------------------------------------------------------ estado "bajo la cama"

    public BedState getBedState() {
        return BedState.byId(this.entityData.get(DATA_BED_STATE));
    }

    public void setBedState(BedState state) {
        this.entityData.set(DATA_BED_STATE, state.ordinal());
    }

    public boolean isCurled() {
        return getBedState() != BedState.NONE;
    }

    @Nullable
    public BlockPos getHiddenBedPos() {
        return hiddenBedPos;
    }

    public void setHiddenBedPos(@Nullable BlockPos pos) {
        this.hiddenBedPos = pos;
    }

    public int getHideCooldown() {
        return hideCooldown;
    }

    public void setHideCooldown(int ticks) {
        this.hideCooldown = ticks;
    }

    /** Devuelve true (una sola vez) si algo pidio que salga de debajo de la cama (por ejemplo, dano). */
    public boolean consumeWakeRequest() {
        boolean value = wakeRequested;
        wakeRequested = false;
        return value;
    }

    /** Sale del estado "bajo la cama" en la posicion actual (usado al morir / emergencias). */
    public void releaseFromBed() {
        this.hiddenBedPos = null;
        setBedState(BedState.NONE);
    }

    /**
     * Busca un hueco libre (con altura completa) alrededor de la cama para salir.
     * Prefiere el hueco mas cercano al objetivo para atacar por sorpresa.
     */
    @Nullable
    public Vec3 findExitPos(Vec3 center) {
        EntityDimensions dims = getType().getDimensions();
        LivingEntity target = getTarget();
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        double[] radii = {1.7D, 2.1D, 2.6D};
        for (double r : radii) {
            for (int i = 0; i < 8; i++) {
                double angle = i * (Math.PI / 4.0D);
                double x = center.x + Math.cos(angle) * r;
                double z = center.z + Math.sin(angle) * r;
                AABB box = dims.makeBoundingBox(x, center.y, z);
                if (!level().noCollision(this, box)) {
                    continue;
                }
                BlockPos below = BlockPos.containing(x, center.y - 0.5D, z);
                if (level().getBlockState(below).getCollisionShape(level(), below).isEmpty()) {
                    continue;
                }
                double score = target != null
                        ? target.distanceToSqr(x, center.y, z)
                        : r * 100.0D + i;
                if (score < bestScore) {
                    bestScore = score;
                    best = new Vec3(x, center.y, z);
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_BED_STATE, BedState.NONE.ordinal());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_BED_STATE.equals(key)) {
            // Bajo la cama no hay colisiones ni gravedad (se coloca "dentro" del bloque de la cama).
            this.noPhysics = isCurled();
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        // Hitbox pequena solo mientras esta tumbado bajo la cama.
        return getBedState() == BedState.HIDDEN ? CURLED_DIMENSIONS : super.getDimensions(pose);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return dimensions.height > 1.0F ? 2.55F : dimensions.height * 0.5F;
    }

    @Override
    public boolean isNoGravity() {
        return super.isNoGravity() || isCurled();
    }

    @Override
    public boolean isPushable() {
        return !isCurled() && super.isPushable();
    }

    /** Un Stalker escondido no impide dormir (asi puede aparecer cuando el jugador duerme). */
    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return !isCurled();
    }

    // ------------------------------------------------------------------ dano

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            amount *= 0.6F; // resistencia a proyectiles
        }
        boolean result = super.hurt(source, amount);
        if (result && !level().isClientSide) {
            if (isCurled()) {
                wakeRequested = true;
            }
            triggerAggression(300);
        }
        return result;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && isCurled()) {
            releaseFromBed();
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide) {
            triggerAnim("attack_controller", "attack");
        }
        return hit;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HideCooldown", hideCooldown);
        if (hiddenBedPos != null && isCurled()) {
            tag.putLong("HiddenBedPos", hiddenBedPos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.hideCooldown = tag.getInt("HideCooldown");
        if (tag.contains("HiddenBedPos")) {
            this.hiddenBedPos = BlockPos.of(tag.getLong("HiddenBedPos"));
            setBedState(BedState.HIDDEN);
        }
    }

    // ------------------------------------------------------------------ sonidos

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        stepCounter++;
        boolean running = isAggressive();
        // Paso lento y pesado: al acechar solo suena una zancada de cada dos.
        if (!running && (stepCounter & 1) == 1) {
            return;
        }
        float volume = running ? 1.1F : 0.9F;
        LivingEntity target = getTarget();
        if (target != null) {
            // Mas fuerte si esta a la espalda del objetivo.
            Vec3 toMe = position().subtract(target.position()).normalize();
            if (target.getViewVector(1.0F).dot(toMe) < -0.3D) {
                volume += 0.5F;
            }
        }
        this.playSound(ModSounds.STEP.get(), volume, 0.85F + random.nextFloat() * 0.2F);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DEATH.get();
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main_controller", 4, this::mainPredicate));
        controllers.add(new AnimationController<>(this, "attack_controller", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ANIM_ATTACK));
    }

    private PlayState mainPredicate(AnimationState<StalkerEntity> state) {
        switch (getBedState()) {
            case ENTERING:
                return state.setAndContinue(ANIM_ENTER_BED);
            case HIDDEN:
                return state.setAndContinue(ANIM_HIDDEN);
            case EXITING:
                return state.setAndContinue(ANIM_EXIT_BED);
            default:
                break;
        }
        if (state.isMoving()) {
            return state.setAndContinue(isAggressive() ? ANIM_RUN : ANIM_WALK);
        }
        return state.setAndContinue(ANIM_IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
