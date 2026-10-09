package com.dimaskama.orthocamera.client;

import com.dimaskama.orthocamera.client.config.ModConfig;
import com.dimaskama.orthocamera.client.config.ModConfigScreen;
import com.dimaskama.orthocamera.duck.ProjectionDuck;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

@Mod(value = OrthoCamera.MOD_ID, dist = Dist.CLIENT)
public class OrthoCamera {

    public static final String MOD_ID = "orthocamera";
    public static final Logger LOGGER = LogManager.getLogger("OrthoCamera");
    public static final ModConfig CONFIG = new ModConfig("config/orthocamera.json",
            "assets/orthocamera/default_config.json");

    public static final KeyMapping.Category KEY_CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(MOD_ID, MOD_ID));

    private static final KeyMapping TOGGLE_KEY =
            createKeybinding("toggle", GLFW.GLFW_KEY_KP_4);
    private static final KeyMapping SCALE_INCREASE_KEY =
            createKeybinding("scale_increase", GLFW.GLFW_KEY_KP_SUBTRACT);
    private static final KeyMapping SCALE_DECREASE_KEY =
            createKeybinding("scale_decrease", GLFW.GLFW_KEY_KP_ADD);
    private static final KeyMapping OPEN_OPTIONS_KEY =
            createKeybinding("options", -1);
    private static final KeyMapping FIX_CAMERA_KEY =
            createKeybinding("fix_camera", GLFW.GLFW_KEY_KP_MULTIPLY);
    private static final KeyMapping FIXED_CAMERA_ROTATE_UP_KEY =
            createKeybinding("fixed_camera_rotate_up", -1);
    private static final KeyMapping FIXED_CAMERA_ROTATE_DOWN_KEY =
            createKeybinding("fixed_camera_rotate_down", -1);
    private static final KeyMapping FIXED_CAMERA_ROTATE_LEFT_KEY =
            createKeybinding("fixed_camera_rotate_left", -1);
    private static final KeyMapping FIXED_CAMERA_ROTATE_RIGHT_KEY =
            createKeybinding("fixed_camera_rotate_right", -1);

    private static final Component ENABLED_TEXT =
            Component.translatable("orthocamera.enabled");
    private static final Component DISABLED_TEXT =
            Component.translatable("orthocamera.disabled");
    private static final Component FIXED_TEXT =
            Component.translatable("orthocamera.fixed");
    private static final Component UNFIXED_TEXT =
            Component.translatable("orthocamera.unfixed");
    private static final float SCALE_MUL_INTERVAL = 1.1F;

    public OrthoCamera(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerKeyMappings);

        CONFIG.loadOrCreate();
        CONFIG.enabled &= CONFIG.save_enabled_state;

        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (Supplier<IConfigScreenFactory>) () -> (container, screen) -> new ModConfigScreen(screen));

        NeoForge.EVENT_BUS.addListener(this::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(this::onClientTickPost);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (CONFIG.isDirty()) {
                CONFIG.save();
            }
        }, "OrthoCamera-Config-Save"));
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(KEY_CATEGORY);
        event.register(TOGGLE_KEY);
        event.register(SCALE_INCREASE_KEY);
        event.register(SCALE_DECREASE_KEY);
        event.register(OPEN_OPTIONS_KEY);
        event.register(FIX_CAMERA_KEY);
        event.register(FIXED_CAMERA_ROTATE_UP_KEY);
        event.register(FIXED_CAMERA_ROTATE_DOWN_KEY);
        event.register(FIXED_CAMERA_ROTATE_LEFT_KEY);
        event.register(FIXED_CAMERA_ROTATE_RIGHT_KEY);
    }

    private void onClientTickPre(ClientTickEvent.Pre event) {
        CONFIG.tick();
    }

    private void onClientTickPost(ClientTickEvent.Post event) {
        handleInput(Minecraft.getInstance());
    }

    private void handleInput(Minecraft client) {
        boolean messageSent = false;

        while (TOGGLE_KEY.consumeClick()) {
            CONFIG.toggle();
            client.gui.chatListener().handleOverlay(CONFIG.enabled ? ENABLED_TEXT : DISABLED_TEXT);
            messageSent = true;
        }

        boolean on = CONFIG.enabled;
        boolean scaleChanged = false;

        while (SCALE_INCREASE_KEY.consumeClick()) {
            if (on) {
                CONFIG.setScaleX(CONFIG.scale_x * SCALE_MUL_INTERVAL);
                CONFIG.setScaleY(CONFIG.scale_y * SCALE_MUL_INTERVAL);
                CONFIG.setDirty(true);
                scaleChanged = true;
            }
        }
        while (SCALE_DECREASE_KEY.consumeClick()) {
            if (on) {
                CONFIG.setScaleX(CONFIG.scale_x / SCALE_MUL_INTERVAL);
                CONFIG.setScaleY(CONFIG.scale_y / SCALE_MUL_INTERVAL);
                CONFIG.setDirty(true);
                scaleChanged = true;
            }
        }

        if (scaleChanged && !messageSent) {
            client.gui.chatListener().handleOverlay(Component.translatable(
                    "orthocamera.scale",
                    String.format("%.1f", CONFIG.scale_x),
                    String.format("%.1f", CONFIG.scale_y)
            ));
            messageSent = true;
        }

        boolean fixPressed = false;
        while (FIX_CAMERA_KEY.consumeClick()) {
            fixPressed = true;
            CONFIG.setFixed(!CONFIG.fixed);
        }
        if (!messageSent && fixPressed) {
            client.gui.chatListener().handleOverlay(CONFIG.fixed ? FIXED_TEXT : UNFIXED_TEXT);
        }

        if (FIXED_CAMERA_ROTATE_LEFT_KEY.isDown()) {
            CONFIG.setFixedYaw(CONFIG.fixed_yaw + CONFIG.fixed_rotate_speed_y);
        }
        if (FIXED_CAMERA_ROTATE_RIGHT_KEY.isDown()) {
            CONFIG.setFixedYaw(CONFIG.fixed_yaw - CONFIG.fixed_rotate_speed_y);
        }
        if (FIXED_CAMERA_ROTATE_UP_KEY.isDown()) {
            CONFIG.setFixedPitch(CONFIG.fixed_pitch + CONFIG.fixed_rotate_speed_x);
        }
        if (FIXED_CAMERA_ROTATE_DOWN_KEY.isDown()) {
            CONFIG.setFixedPitch(CONFIG.fixed_pitch - CONFIG.fixed_rotate_speed_x);
        }

        boolean openScreen = false;
        while (OPEN_OPTIONS_KEY.consumeClick()) {
            openScreen = true;
        }
        if (openScreen) {
            client.gui.setScreen(new ModConfigScreen(null));
        }
    }

    public static boolean isEnabled() {
        return CONFIG.enabled;
    }

    public static Matrix4f createOrthoMatrixForCulling() {
        Minecraft client = Minecraft.getInstance();
        float width = CONFIG.scale_x * client.getWindow().getWidth()
                / client.getWindow().getHeight();
        float height = CONFIG.scale_y;
        return new Matrix4f().setOrtho(
                -3.0F * width, 3.0F * width,
                -3.0F * height, 3.0F * height,
                CONFIG.min_distance, CONFIG.max_distance
        );
    }

    public static void setupOrthoMatrix(Projection projection, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        float width = CONFIG.getScaleX(tickDelta) * client.getWindow().getWidth()
                / client.getWindow().getHeight();
        float height = CONFIG.getScaleY(tickDelta);
        float len = CONFIG.max_distance - CONFIG.min_distance;
        projection.setupOrtho(len, -0.5F * len, 2 * width, 2 * height, false);
        ((ProjectionDuck) projection).orthocamera_setIsOrthocamera(true);
    }

    private static KeyMapping createKeybinding(String name, int key) {
        return new KeyMapping(
                "orthocamera.key." + name,
                InputConstants.Type.KEYSYM,
                key,
                KEY_CATEGORY
        );
    }
}