package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.Toast;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

/** toast's rabbit model*/
public final class ToastModel extends HierarchicalModel<Toast> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "bunny"), "main");

    private final ModelPart root;
    private final ModelPart body, head, leftEar, rightEar, tail;
    private final ModelPart leftFrontLeg, rightFrontLeg, leftHindLeg, rightHindLeg, leftHaunch, rightHaunch;
    private final List<ModelPart> hopping;

    public ToastModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body");
        head = root.getChild("head");
        leftEar = head.getChild("left_ear");
        rightEar = head.getChild("right_ear");
        tail = root.getChild("tail");
        leftFrontLeg = root.getChild("left_front_leg");
        rightFrontLeg = root.getChild("right_front_leg");
        leftHindLeg = root.getChild("left_hind_leg");
        rightHindLeg = root.getChild("right_hind_leg");
        leftHaunch = root.getChild("left_haunch");
        rightHaunch = root.getChild("right_haunch");
        hopping = List.of(body, head, tail, leftFrontLeg, rightFrontLeg,
                leftHindLeg, rightHindLeg, leftHaunch, rightHaunch);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", box(0, 0, -3, -2, -10, 6, 5, 10), PartPose.offset(0, 19, 8));
        PartDefinition head = root.addOrReplaceChild("head", box(32, 0, -2.5F, -4, -4, 5, 4, 5), PartPose.offset(0, 16, -1));
        head.addOrReplaceChild("left_ear", box(52, 0, -2.5F, -9, -1, 2, 5, 1), PartPose.ZERO);
        head.addOrReplaceChild("right_ear", box(58, 0, 0.5F, -9, -1, 2, 5, 1), PartPose.ZERO);
        root.addOrReplaceChild("tail", box(52, 6, -1.5F, -1.5F, 0, 3, 3, 2), PartPose.offset(0, 17, 8));
        root.addOrReplaceChild("left_front_leg", box(8, 15, -1, 0, -1, 2, 7, 2), PartPose.offset(3, 17, -1));
        root.addOrReplaceChild("right_front_leg", box(0, 15, -1, 0, -1, 2, 7, 2), PartPose.offset(-3, 17, -1));
        root.addOrReplaceChild("left_hind_leg", box(30, 15, -1, 5.5F, -3.7F, 2, 1, 7), PartPose.offset(3, 17.5F, 3.7F));
        root.addOrReplaceChild("right_hind_leg", box(16, 15, -1, 5.5F, -3.7F, 2, 1, 7), PartPose.offset(-3, 17.5F, 3.7F));
        root.addOrReplaceChild("left_haunch", box(16, 24, -1, 0, 0, 2, 4, 5), PartPose.offset(3, 17.5F, 3.7F));
        root.addOrReplaceChild("right_haunch", box(0, 24, -1, 0, 0, 2, 4, 5), PartPose.offset(-3, 17.5F, 3.7F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static CubeListBuilder box(int u, int v, float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d);
    }

    @Override
    public ModelPart root() { return root; }

    @Override
    public void setupAnim(Toast toast, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);

        head.xRot = headPitch * Mth.DEG_TO_RAD;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;

        float hop = Mth.sin(limbSwing * 1.5F);
        float hopAmount = Math.abs(hop) * limbSwingAmount;
        float lift = Math.max(0, hop) * limbSwingAmount * 4.0F;
        for (ModelPart part : hopping) part.y -= lift;

        leftFrontLeg.xRot = rightFrontLeg.xRot = -hop * 0.8F * limbSwingAmount;
        float back = -hop * 1.2F * limbSwingAmount;
        leftHaunch.xRot = rightHaunch.xRot = leftHindLeg.xRot = rightHindLeg.xRot = back;
        body.xRot = hop * 0.1F * limbSwingAmount;

        float earWiggle = Mth.cos(ageInTicks * 0.09F) * 0.1F;
        leftEar.zRot = earWiggle - 0.05F + hopAmount * 0.2F;
        rightEar.zRot = -earWiggle + 0.05F - hopAmount * 0.2F;
        tail.yRot = Mth.sin(ageInTicks * 0.15F) * 0.2F;
        tail.xRot = -hopAmount * 0.3F;
    }
}
