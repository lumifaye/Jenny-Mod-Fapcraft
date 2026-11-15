package com.trolmastercard.sexmod.client.renderer;

import com.trolmastercard.sexmod.client.model.EllieModel;
import com.trolmastercard.sexmod.entity.EllieEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.math.Vec3d;

/** Renders Ellie; only her weapon angles differ from the other girls. */
public class EllieRenderer extends GirlRenderer<EllieEntity> {
   public EllieRenderer(EntityRendererFactory.Context context) {
      super(context, new EllieModel(), -85.0F, -175.0F, -85.0F, new Vec3d(-0.1, 0.05, 0.0));
   }
}
