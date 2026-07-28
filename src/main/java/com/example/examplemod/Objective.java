package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.HolderLookup;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class Objective {
    public static final Codec<Objective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(Objective::getId),
        ComponentSerialization.CODEC.fieldOf("displayName").forGetter(Objective::getDisplayName),
        ItemStack.CODEC.optionalFieldOf("icon", ItemStack.EMPTY).forGetter(Objective::getIcon),
        Codec.STRING.fieldOf("type").forGetter(o -> o.getType().name()),
        Codec.STRING.optionalFieldOf("advancementId").forGetter(o -> Optional.ofNullable(o.getAdvancementId())),
        ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(Objective::getDescription),
        Codec.BOOL.optionalFieldOf("forceCompleted", false).forGetter(Objective::isForceCompleted)
    ).apply(instance, (id, name, icon, typeStr, adv, desc, forced) -> {
        Objective o = new Objective(id, name, icon, Type.valueOf(typeStr), adv.orElse(null), desc);
        o.setForceCompleted(forced);
        return o;
    }));
    public enum Type {
        ITEM,
        BLOCK,
        ADVANCEMENT
    }

    private final String id;
    private final Component displayName;
    private final ItemStack icon;
    private final Type type;
    private final String advancementId;
    private final Component description;
    private boolean forceCompleted = false;

    public Objective(String id, Component displayName, ItemStack icon, Type type, String advancementId, Component description) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.type = type;
        this.advancementId = advancementId;
        this.description = description != null ? description : Component.empty();
    }
    
    public Objective(String id, Component displayName, ItemStack icon, Type type, String advancementId) {
        this(id, displayName, icon, type, advancementId, Component.empty());
    }
    
    public Objective(String id, Component displayName, ItemStack icon, Type type) {
        this(id, displayName, icon, type, null, Component.empty());
    }

    public String getId() { return id; }
    public Component getDisplayName() { return displayName; }
    public ItemStack getIcon() { return icon; }
    public Type getType() { return type; }
    public String getAdvancementId() { return advancementId; }
    public Component getDescription() { return description; }
    public boolean isForceCompleted() { return forceCompleted; }
    public void setForceCompleted(boolean forceCompleted) { this.forceCompleted = forceCompleted; }

    public boolean isCompleted(Player player) {
        // forceCompleted is used in cooperative mode to share advancement progress across the team.
        if (forceCompleted) return true;
        
        if (type == Type.ITEM || type == Type.BLOCK) {
            // Check inventory for item
            // Note: For blocks, we check the item form
            if (icon.isEmpty()) return false;
            return player.getInventory().contains(icon);
        }
        
        if (type == Type.ADVANCEMENT && advancementId != null) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                try {
                    net.minecraft.server.MinecraftServer server = null;
                    if (serverPlayer.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        server = serverLevel.getServer();
                    }
                    
                    if (server != null) {
                        for (net.minecraft.advancements.AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
                            if (holder.id().toString().equals(advancementId)) {
                                return serverPlayer.getAdvancements().getOrStartProgress(holder).isDone();
                            }
                        }
                    }
                } catch (Throwable e) {
                    // Ignore
                }
            } else {
                // Client-side: check this player's own advancement progress (needed for Challenge mode)
                return isAdvancementDoneOnClient(advancementId);
            }
        }
        
        return false;
    }

    private static boolean isAdvancementDoneOnClient(String advId) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.player.connection == null) return false;
            net.minecraft.client.multiplayer.ClientAdvancements advancements = mc.player.connection.getAdvancements();
            if (advancements == null) return false;

            net.minecraft.resources.Identifier loc = net.minecraft.resources.Identifier.tryParse(advId);
            if (loc == null) return false;

            for (java.util.Map.Entry<net.minecraft.advancements.AdvancementHolder, net.minecraft.advancements.AdvancementProgress> entry : advancements.progress.entrySet()) {
                if (entry.getKey().id().equals(loc)) {
                    return entry.getValue().isDone();
                }
            }
        } catch (Throwable t) {
            // Ignore errors
        }
        return false;
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        
        try {
            JsonElement json = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, displayName).getOrThrow(IllegalStateException::new);
            tag.putString("displayName", json.toString());
        } catch (Exception e) {
            tag.putString("displayName", displayName.getString());
        }

        try {
             // Use CODEC for ItemStack
             // icon is ItemStack
             // encodeStart returns DataResult<Tag>
             // We need to put it into a wrapper tag or merge?
             // Usually icon field is a CompoundTag.
             
             // ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, icon) -> Tag
             net.minecraft.nbt.Tag itemTag = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, icon).result().orElse(new CompoundTag());
             tag.put("icon", itemTag);
        } catch (Throwable e) {
             tag.put("icon", new CompoundTag());
        }

        tag.putString("type", type.name());
        if (advancementId != null) tag.putString("advancementId", advancementId);
        
        try {
            JsonElement json = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, description).getOrThrow(IllegalStateException::new);
            tag.putString("description", json.toString());
        } catch (Exception e) {
            tag.putString("description", description.getString());
        }
        
        tag.putBoolean("forceCompleted", forceCompleted);
        
        return tag;
    }

    public static Objective load(CompoundTag tag, HolderLookup.Provider provider) {
        String id = "";
        try {
            Object idObj = tag.getString("id");
            if (idObj instanceof Optional) {
                id = ((Optional<String>) idObj).orElse("");
            } else {
                id = (String) idObj;
            }
        } catch (Throwable e) { id = ""; }

        Component displayName = Component.literal(id);
        try {
            if (tag.contains("displayName")) {
                String json = "";
                Object jsonObj = tag.getString("displayName");
                if (jsonObj instanceof Optional) {
                    json = ((Optional<String>) jsonObj).orElse("");
                } else {
                    json = (String) jsonObj;
                }
                
                if (!json.isEmpty()) {
                     try {
                         JsonElement element = JsonParser.parseString(json);
                         displayName = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(Component.literal(id));
                     } catch (Exception ex) {
                         displayName = Component.literal(json);
                     }
                }
            }
        } catch (Throwable e) {}
        
        Component description = Component.empty();
        try {
            if (tag.contains("description")) {
                String json = "";
                Object jsonObj = tag.getString("description");
                if (jsonObj instanceof Optional) {
                    json = ((Optional<String>) jsonObj).orElse("");
                } else {
                    json = (String) jsonObj;
                }
                
                if (!json.isEmpty()) {
                     try {
                         JsonElement element = JsonParser.parseString(json);
                         description = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(Component.empty());
                     } catch (Exception ex) {
                         description = Component.literal(json);
                     }
                }
            }
        } catch (Throwable e) {}

        ItemStack icon = ItemStack.EMPTY;
        try {
            if (tag.contains("icon")) {
                 Object iconTagObj = tag.get("icon");
                 net.minecraft.nbt.Tag iconTag = null;
                 if (iconTagObj instanceof Optional) {
                      iconTag = ((Optional<net.minecraft.nbt.Tag>) iconTagObj).orElse(null);
                 } else {
                      iconTag = (net.minecraft.nbt.Tag) iconTagObj;
                 }
                 
                 if (iconTag != null) {
                      icon = ItemStack.CODEC.parse(NbtOps.INSTANCE, iconTag).result().orElse(ItemStack.EMPTY);
                 }
            }
        } catch (Throwable e) {
            icon = ItemStack.EMPTY;
        }

        Type type = Type.ITEM;
        try {
             Object typeObj = tag.getString("type");
             String typeStr = "ITEM";
             if (typeObj instanceof Optional) {
                 typeStr = ((Optional<String>) typeObj).orElse("ITEM");
             } else {
                 typeStr = (String) typeObj;
             }
             type = Type.valueOf(typeStr);
        } catch (Throwable e) {}

        String advancementId = null;
        try {
             if (tag.contains("advancementId")) {
                 Object advObj = tag.getString("advancementId");
                 if (advObj instanceof Optional) {
                     advancementId = ((Optional<String>) advObj).orElse(null);
                 } else {
                     advancementId = (String) advObj;
                 }
             }
        } catch (Throwable e) {}
        
        Objective obj = new Objective(id, displayName, icon, type, advancementId, description);
        
        try {
             if (tag.contains("forceCompleted")) {
                 Object boolObj = tag.getBoolean("forceCompleted");
                 if (boolObj instanceof Optional) {
                      obj.setForceCompleted(((Optional<Boolean>) boolObj).orElse(false));
                 } else {
                      obj.setForceCompleted((boolean) boolObj);
                 }
             }
        } catch (Throwable e) {}
        
        return obj;
    }
}
