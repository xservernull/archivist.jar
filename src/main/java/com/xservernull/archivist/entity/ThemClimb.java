// lets them overcome obstacles by digging, building upward, and creating dirt bridges toward its target.

package com.xservernull.archivist.entity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

final class ThemClimb extends Goal {
    private static final int STUCK_TRIGGER         = 12;
    private static final int GLOBAL_COOLDOWN       = 60;
    private static final int BUILD_TICKS_PER_BLOCK = 2;
    private static final int MINE_TICKS_PER_BLOCK  = 40;
    private static final int MAX_MINE_HEIGHT        = 3;
    private static final int MAX_COLUMN_HEIGHT      = 14;
    private static final int MAX_BRIDGE_LENGTH      = 20;
    private enum Phase { MINE, BUILD_UP, BUILD_ACROSS, DIG_DOWN, DONE }
    private final Them mob;
    private Vec3 lastPos    = Vec3.ZERO;
    private int  stuckTicks = 0;
    private int  cooldown   = 0;
    private Phase    phase           = Phase.DONE;
    private int      buildTimer      = 0;
    private BlockPos mineTarget      = null;
    private int      mineTick        = 0;
    private int      mineLayersDone  = 0;
    private BlockPos columnBase      = null;
    private int      columnTopY      = 0;
    private int      columnHeight    = 0;
    private int      targetColumnH   = 0;
    private int      bridgeSteps     = 0;
    private int      bridgeDX        = 0;
    private int      bridgeDZ        = 0;
    private int      targetBridgeLen = 0;
    private BlockPos digTarget      = null;
    private int      digTick        = 0;
    private int      digTargetY     = 0;
    ThemClimb(Them mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }
    @Override
    public boolean canUse() {
        if (mob.getTarget() == null)    return false;
        if (mob.level().isClientSide()) return false;
        if (cooldown > 0) { cooldown--; return false; }
        Vec3 cur = mob.position();
        double moved = new Vec3(cur.x - lastPos.x, 0, cur.z - lastPos.z).lengthSqr();
        lastPos = cur;
        if (moved < 0.005) stuckTicks++;
        else               { stuckTicks = 0; return false; }
        return stuckTicks >= STUCK_TRIGGER;
    }
    @Override
    public boolean canContinueToUse() {
        if (phase == Phase.DONE) return false;
        if (mob.isDeadOrDying()) return false;
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && !mob.level().isClientSide();
    }
    @Override
    public void start() {
        stuckTicks    = 0;
        buildTimer    = 0;
        columnHeight  = 0;
        bridgeSteps   = 0;
        mineTick      = 0;
        mineLayersDone = 0;
        mineTarget    = null;
        digTarget     = null;
        digTick       = 0;
        LivingEntity target = mob.getTarget();
        if (target == null) { phase = Phase.DONE; return; }
        mob.getNavigation().stop();
        double dy = target.getY() - mob.getY();
        if (dy < -1.5) {
            digTargetY = (int) Math.floor(target.getY());
            phase = Phase.DIG_DOWN;
            pickNextDigTarget();
            return;
        }
        Vec3 toTarget = target.position().subtract(mob.position())
                .multiply(1, 0, 1).normalize();
        if (toTarget.lengthSqr() < 0.001) { phase = Phase.DONE; return; }
        bridgeDX = Math.max(-1, Math.min(1, (int) Math.round(toTarget.x)));
        bridgeDZ = Math.max(-1, Math.min(1, (int) Math.round(toTarget.z)));
        if (bridgeDX == 0 && bridgeDZ == 0) bridgeDX = 1;
        BlockPos feet = mob.blockPosition();
        columnBase = feet.offset(bridgeDX, 0, bridgeDZ);
        columnTopY = columnBase.getY() - 1;
        int heightNeeded = (int) Math.ceil(target.getY() - mob.getY()) + 1;
        targetColumnH = Math.max(2, Math.min(heightNeeded, MAX_COLUMN_HEIGHT));
        double horizDist = Math.sqrt(
                Math.pow(target.getX() - mob.getX(), 2) +
                        Math.pow(target.getZ() - mob.getZ(), 2));
        targetBridgeLen = Math.min((int) Math.ceil(horizDist), MAX_BRIDGE_LENGTH);
        if (hasSolidBlockAhead()) {
            phase = Phase.MINE;
            pickNextMineTarget();
        } else {
            phase = Phase.BUILD_UP;
        }
    }
    @Override
    public void stop() {
        clearMineProgress();
        clearDigProgress();
        phase    = Phase.DONE;
        cooldown = GLOBAL_COOLDOWN;
        LivingEntity target = mob.getTarget();
        if (target != null) mob.getNavigation().moveTo(target, 1.0);
    }
    @Override
    public void tick() {
        if (mob.isDeadOrDying()) { phase = Phase.DONE; return; }
        LivingEntity target = mob.getTarget();
        if (target == null) { phase = Phase.DONE; return; }
        Level level = mob.level();
        boolean canPin = mob.hurtTime == 0 && !mob.isDeadOrDying();
        if (canPin && phase == Phase.BUILD_UP && columnBase != null) {
            double colSurface = columnTopY + 1.0;
            if (mob.getY() < colSurface) {
                mob.setPos(mob.getX(), colSurface, mob.getZ());
                Vec3 dv = mob.getDeltaMovement();
                mob.setDeltaMovement(dv.x, 0.0, dv.z);
            }
        } else if (canPin && phase == Phase.BUILD_ACROSS && columnBase != null) {
            double bridgeSurface = columnTopY + 1.0;
            double currentY = mob.getY();
            if (currentY < bridgeSurface - 0.05 || currentY > bridgeSurface + 0.6) {
                mob.setPos(mob.getX(), bridgeSurface, mob.getZ());
            }
            Vec3 dv = mob.getDeltaMovement();
            if (dv.y < 0) mob.setDeltaMovement(dv.x, 0.0, dv.z);
        }
        switch (phase) {
            case MINE -> {
                if (mineTarget == null) {
                    phase = Phase.BUILD_UP;
                    return;
                }
                if (mineTarget == null || level.getBlockState(mineTarget).isAir()) {
                    clearMineProgress();
                    mineLayersDone++;
                    if (mineLayersDone >= MAX_MINE_HEIGHT || !hasSolidBlockAhead()) {
                        phase = Phase.BUILD_UP;
                    } else {
                        pickNextMineTarget();
                    }
                    return;
                }
                mineTick++;
                if (mineTarget != null && level instanceof ServerLevel serverLevel) {
                    int stage = Math.min(9, (mineTick * 10) / MINE_TICKS_PER_BLOCK);
                    serverLevel.destroyBlockProgress(mob.getId(), mineTarget, stage);
                }
                if (mineTick >= MINE_TICKS_PER_BLOCK) {
                    if (mineTarget != null) {
                        clearMineProgress();
                        level.removeBlockEntity(mineTarget);
                        level.destroyBlock(mineTarget, true, mob);
                    }
                    mineLayersDone++;
                    mineTick = 0;
                    if (mineLayersDone >= MAX_MINE_HEIGHT || !hasSolidBlockAhead()) {
                        phase = Phase.BUILD_UP;
                    } else {
                        pickNextMineTarget();
                    }
                }
            }
            case BUILD_UP -> {
                buildTimer++;
                if (buildTimer < BUILD_TICKS_PER_BLOCK) return;
                buildTimer = 0;
                if (columnHeight >= targetColumnH) {
                    bridgeSteps = 0;
                    phase = Phase.BUILD_ACROSS;
                    return;
                }
                columnTopY++;
                BlockPos placeAt = new BlockPos(columnBase.getX(), columnTopY, columnBase.getZ());
                placeIfReplaceable(level, placeAt);
                columnHeight++;
                mob.setPos(mob.getX(), columnTopY + 1.0, mob.getZ());
                mob.setDeltaMovement(0, 0, 0);
            }
            case BUILD_ACROSS -> {
                buildTimer++;
                if (buildTimer < BUILD_TICKS_PER_BLOCK) return;
                buildTimer = 0;
                if (mob.distanceToSqr(target) < 4.0 || bridgeSteps >= targetBridgeLen) {
                    phase = Phase.DONE;
                    return;
                }
                int step = bridgeSteps + 1;
                BlockPos bridgeBlock = new BlockPos(
                        columnBase.getX() + bridgeDX * step,
                        columnTopY,
                        columnBase.getZ() + bridgeDZ * step);
                placeIfReplaceable(level, bridgeBlock);
                bridgeSteps++;
                mob.setPos(bridgeBlock.getX() + 0.5, columnTopY + 1.0, bridgeBlock.getZ() + 0.5);
                mob.setDeltaMovement(0, 0, 0);
                mob.hasImpulse = true;
            }
            case DIG_DOWN -> {
                LivingEntity dig_target = mob.getTarget();
                if (dig_target == null || mob.blockPosition().getY() <= digTargetY) {
                    phase = Phase.DONE;
                    return;
                }
                if (digTarget == null) {
                    pickNextDigTarget();
                    if (digTarget == null) { phase = Phase.DONE; return; }
                }
                if (digTarget == null || level.getBlockState(digTarget).isAir()) {
                    clearDigProgress();
                    mob.setDeltaMovement(mob.getDeltaMovement().x, -0.2, mob.getDeltaMovement().z);
                    pickNextDigTarget();
                    return;
                }
                digTick++;
                if (digTarget != null && level instanceof ServerLevel serverLevel) {
                    int stage = Math.min(9, (digTick * 10) / MINE_TICKS_PER_BLOCK);
                    serverLevel.destroyBlockProgress(mob.getId(), digTarget, stage);
                }
                if (digTick >= MINE_TICKS_PER_BLOCK) {
                    if (digTarget != null) {
                        BlockPos digPos = digTarget;
                        clearDigProgress();
                        level.removeBlockEntity(digPos);
                        level.destroyBlock(digPos, true, mob);
                    }
                    digTick = 0;
                    mob.setDeltaMovement(mob.getDeltaMovement().x, -0.3, mob.getDeltaMovement().z);
                    pickNextDigTarget();
                }
            }
            case DONE -> {  }
        }
    }
    private boolean hasSolidBlockAhead() {
        if (columnBase == null) return false;
        Level level = mob.level();
        BlockPos feet = mob.blockPosition();
        for (int h = 0; h <= 1; h++) {
            if (level.getBlockState(feet.offset(bridgeDX, h, bridgeDZ)).isSolid()) return true;
        }
        return false;
    }
    private void pickNextMineTarget() {
        Level level = mob.level();
        BlockPos feet = mob.blockPosition();
        for (int h = 0; h <= MAX_MINE_HEIGHT; h++) {
            BlockPos check = feet.offset(bridgeDX, h, bridgeDZ);
            if (level.getBlockState(check).isSolid()) {
                mineTarget = check;
                mineTick   = 0;
                return;
            }
        }
        mineTarget = null;
    }
    private void clearMineProgress() {
        if (mineTarget != null && mob.level() instanceof ServerLevel serverLevel) {
            serverLevel.destroyBlockProgress(mob.getId(), mineTarget, -1);
        }
    }
    private void pickNextDigTarget() {
        BlockPos feet = mob.blockPosition();
        for (int offset = 0; offset >= -2; offset--) {
            BlockPos candidate = feet.offset(0, offset, 0);
            if (candidate.getY() < digTargetY) { digTarget = null; return; }
            BlockState bs = mob.level().getBlockState(candidate);
            if (!bs.isAir() && bs.getDestroySpeed(mob.level(), candidate) >= 0) {
                digTarget = candidate;
                digTick   = 0;
                return;
            }
        }
        digTarget = null;
    }
    private void clearDigProgress() {
        if (digTarget != null && mob.level() instanceof ServerLevel serverLevel) {
            serverLevel.destroyBlockProgress(mob.getId(), digTarget, -1);
            digTarget = null;
        }
    }
    private void placeIfReplaceable(Level level, BlockPos pos) {
        BlockState bs = level.getBlockState(pos);
        if (bs.isAir() || bs.canBeReplaced()) {
            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        }
    }
}