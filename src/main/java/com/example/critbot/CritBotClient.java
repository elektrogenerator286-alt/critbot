package com.example.critbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;

/**
 * Client-side only. Key is rebindable in Options -> Controls -> Miscellaneous.
 * Targets only visible (non-invisible) mobs/players wearing or holding netherite gear.
 */
public class CritBotClient implements ClientModInitializer {
    static final double REACH = 3.0;          // attack range (vanilla survival is 3.0)
    static final double THREAT_RANGE = 3.8;   // dodge when a threat is closer than this
    static final int DODGE_COOLDOWN = 12;     // ticks

    private static KeyMapping toggleKey;
    private boolean enabled = false;
    private int dodgeCd = 0;
    private int dodgeDir = 1;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.critbot.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            if (mc.player != null) mc.player.displayClientMessage(
                    Component.literal("CritBot: " + (enabled ? "ON" : "OFF")), true);
        }
        LocalPlayer p = mc.player;
        if (!enabled || p == null || mc.level == null || mc.gameMode == null || mc.screen != null || !p.isAlive()) return;
        if (dodgeCd > 0) dodgeCd--;

        LivingEntity target = nearestEnemy(mc, p, THREAT_RANGE + 1.0);
        if (target == null) return;

        double dist = p.distanceTo(target);
        faceTarget(p, target);

        // ---- auto-dodge: sidestep perpendicular to the threat when it gets close ----
        if (dist < THREAT_RANGE && dodgeCd == 0 && p.onGround()) {
            Vec3 toThreat = target.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            Vec3 side = new Vec3(-toThreat.z, 0, toThreat.x).scale(dodgeDir);
            p.setDeltaMovement(p.getDeltaMovement().add(side.scale(0.55)).add(0, 0.0, 0));
            dodgeDir = -dodgeDir;
            dodgeCd = DODGE_COOLDOWN;
        }

        // ---- auto-crit: jump, then hit on the way down with a charged weapon ----
        if (dist <= REACH + 0.5) {
            if (p.onGround() && dodgeCd < DODGE_COOLDOWN - 3) {
                p.jumpFromGround();
            }
            boolean canCrit = !p.onGround() && p.fallDistance > 0.0F && p.getDeltaMovement().y < 0
                    && !p.onClimbable() && !p.isInWater() && !p.isPassenger()
                    && !p.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
            if (canCrit && p.getAttackStrengthScale(0.5F) >= 0.95F && dist <= REACH) {
                mc.gameMode.attack(p, target);
                p.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    private static LivingEntity nearestEnemy(Minecraft mc, LocalPlayer p, double range) {
        AABB box = p.getBoundingBox().inflate(range);
        return mc.level.getEntitiesOfClass(LivingEntity.class, box, e ->
                        e != p && e.isAlive() && !e.isInvisible()
                                && (e instanceof Monster || e instanceof Player)
                                && wearsNetherite(e) && p.hasLineOfSight(e))
                .stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
    }

    /** True if the entity wears or holds anything netherite (armor, sword, axe, ...). */
    private static boolean wearsNetherite(LivingEntity e) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack st = e.getItemBySlot(slot);
            if (!st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().startsWith("netherite_")) return true;
        }
        return false;
    }

    private static void faceTarget(LocalPlayer p, LivingEntity t) {
        Vec3 d = t.getEyePosition().subtract(p.getEyePosition());
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        p.setYRot((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0));
        p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, h)));
    }
}
