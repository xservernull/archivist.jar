package com.xservernull.archivist.client;

import com.xservernull.archivist.entity.Statue;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import java.util.Random;

/**
 * the statue's model
 */
final class StatueModel extends GeoModel<Statue> {
    @Override
    public ResourceLocation getModelResource(Statue statue) { return EntityAssets.geometry(statue); }
    @Override
    public ResourceLocation getTextureResource(Statue statue) { return EntityAssets.texture(statue); }
    @Override
    public ResourceLocation getAnimationResource(Statue statue) { return EntityAssets.animations(statue); }

    @Override
    public void setCustomAnimations(Statue statue, long instanceId, AnimationState<Statue> state) {
        super.setCustomAnimations(statue, instanceId, state);
        if (statue.getDeltaMovement().horizontalDistanceSqr() > 0.001D) return;
        Random random = new Random(statue.getUUID().getLeastSignificantBits());
        pose("head", angle(random, -20, 20), angle(random, -30, 30), 0.0F);
        pose("right_arm", angle(random, -150, 30), 0.0F, angle(random, -25, 25));
        pose("left_arm", angle(random, -150, 30), 0.0F, angle(random, -25, 25));
        pose("right_leg", angle(random, -25, 25), 0.0F, 0.0F);
        pose("left_leg", angle(random, -25, 25), 0.0F, 0.0F);
    }

    private void pose(String name, float x, float y, float z) {
        var bone = getAnimationProcessor().getBone(name);
        if (bone == null) return;
        bone.setRotX(bone.getRotX() + x);
        bone.setRotY(bone.getRotY() + y);
        bone.setRotZ(bone.getRotZ() + z);
    }

    private static float angle(Random random, float min, float max) {
        return (float) Math.toRadians(min + random.nextFloat() * (max - min));
    }
}
