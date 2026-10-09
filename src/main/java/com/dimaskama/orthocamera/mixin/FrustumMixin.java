package com.dimaskama.orthocamera.mixin;

import com.dimaskama.orthocamera.client.OrthoCamera;
import com.dimaskama.orthocamera.duck.FrustumDuck;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
abstract class FrustumMixin implements FrustumDuck {

    @Unique
    private boolean orthocamera_isOrthocamera;

    @Override
    public void orthocamera_setIsOrthocamera(boolean isOrthocamera) {
        orthocamera_isOrthocamera = isOrthocamera;
    }

    @Override
    public boolean orthocamera_isOrthocamera() {
        return orthocamera_isOrthocamera;
    }

    @Inject(method = "set", at = @At("TAIL"))
    private void setTail(Frustum frustum, CallbackInfo ci) {
        orthocamera_isOrthocamera = ((FrustumDuck) frustum).orthocamera_isOrthocamera();
    }

    @Inject(
            method = "isVisible",
            at = @At("HEAD"),
            cancellable = true
    )
    private void modifyIsVisible(
            AABB box,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!orthocamera_isOrthocamera) {
            return;
        }

        // Limitar el rango Z para no desbordar los buffers internos de Sodium.
        // Sin este limite, Sodium intenta construir todos los chunks dentro de
        // la vista ortografica y escribe en memoria nula (crash nativo).
        double minZ = OrthoCamera.CONFIG.min_distance;
        double maxZ = OrthoCamera.CONFIG.max_distance;
        double centerZ = (box.minZ + box.maxZ) * 0.5;

        if (centerZ >= minZ && centerZ <= maxZ) {
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "cubeInFrustum(DDDDDD)I", at = @At("HEAD"), cancellable = true)
    private void modifyCubeInFrustum(CallbackInfoReturnable<Integer> cir) {
        if (orthocamera_isOrthocamera) {
            cir.setReturnValue(FrustumIntersection.INSIDE);
        }
    }

    @Inject(method = "pointInFrustum", at = @At("HEAD"), cancellable = true)
    private void modifyPointInFrustum(CallbackInfoReturnable<Boolean> cir) {
        if (orthocamera_isOrthocamera) {
            cir.setReturnValue(true);
        }
    }
}