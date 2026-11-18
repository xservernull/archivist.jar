package com.xservernull.archivist.client;

import com.xservernull.archivist.entity.Him;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import java.util.concurrent.ThreadLocalRandom;

/**
 * him's model
 */
final class HimModel extends GeoModel<Him> {
    private static final float MAX_LEAN_DEG = 100.0F, JITTER_DEG = 2.0F;
    /** How far the stretch pose goes while he is wild but not stretching, 0 to 1 (1 = the full 100 degree lean). */
    private static final float WILD_STRENGTH = 1.0F;

    @Override
    public ResourceLocation getModelResource(Him him) { return EntityAssets.geometry(him); }
    @Override
    public ResourceLocation getTextureResource(Him him) { return EntityAssets.texture(him); }
    @Override
    public ResourceLocation getAnimationResource(Him him) { return EntityAssets.animations(him); }

    @Override
    public void setCustomAnimations(Him him, long instanceId, AnimationState<Him> state) {
        super.setCustomAnimations(him, instanceId, state);
        float progress = him.getStretchProgress();
        if (progress < 0.0F) {
            if (!him.isWild()) return;
            progress = WILD_STRENGTH;
        }
        float envelope = Math.max(0.0F, progress * progress + (rnd() - 0.5F) * 0.08F);
        float lean = (float) Math.toRadians(MAX_LEAN_DEG * envelope);
        twist("body", lean + jitter(), 0.0F, 0.0F);
        twist("head", lean * 0.4F + jitter() * 0.5F, 0.0F, 0.0F);
        for (String limb : new String[] {"leftArm", "rightArm", "leftLeg", "rightLeg"}) {
            twist(limb, jitter(), 0.0F, jitter() * 0.6F);
        }
    }

    private void twist(String name, float x, float y, float z) {
        var bone = getAnimationProcessor().getBone(name);
        if (bone == null) return;
        bone.setRotX(bone.getRotX() + x);
        bone.setRotY(bone.getRotY() + y);
        bone.setRotZ(bone.getRotZ() + z);
    }

    private static float rnd() { return ThreadLocalRandom.current().nextFloat(); }

    private static float jitter() { return (float) Math.toRadians((rnd() - 0.5F) * 2.0F * JITTER_DEG); }
}