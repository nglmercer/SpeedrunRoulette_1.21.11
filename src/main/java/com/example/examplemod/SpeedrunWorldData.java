package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.core.HolderLookup;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.Optional;
import net.minecraft.world.level.saveddata.SavedDataType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class SpeedrunWorldData extends SavedData {

    public static final Codec<SpeedrunWorldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Objective.CODEC.listOf().fieldOf("objectives").forGetter(SpeedrunWorldData::getObjectives)
    ).apply(instance, (objectives) -> {
        SpeedrunWorldData data = new SpeedrunWorldData();
        data.setObjectives(objectives);
        return data;
    }));

    private static final String DATA_NAME = "speedrun_world_data";
    private final List<Objective> objectives = new ArrayList<>();
    private boolean runInfoVictory = false;
    private String runInfoTime = "";
    private String runInfoObjective = "";

    public SpeedrunWorldData() {}

    public static SpeedrunWorldData load(CompoundTag tag, HolderLookup.Provider provider) {
        SpeedrunWorldData data = new SpeedrunWorldData();
        data.objectives.clear();
        if (tag.contains("objectives")) {
            Object listObj = tag.getList("objectives");
            ListTag list = null;
            if (listObj instanceof Optional) {
                list = ((Optional<ListTag>) listObj).orElse(null);
            } else {
                list = (ListTag) listObj;
            }

            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    try {
                        Object objTagObj = list.getCompound(i);
                        CompoundTag objTag = null;
                        if (objTagObj instanceof Optional) {
                            objTag = ((Optional<CompoundTag>) objTagObj).orElse(null);
                        } else {
                            objTag = (CompoundTag) objTagObj;
                        }

                        if (objTag != null) {
                            data.objectives.add(Objective.load(objTag, provider));
                        }
                    } catch (Throwable e) {
                    }
                }
            }
        }
        if (tag.contains("runInfoVictory")) {
            data.runInfoVictory = tag.getBoolean("runInfoVictory").orElse(false);
        }
        if (tag.contains("runInfoTime")) {
            data.runInfoTime = tag.getString("runInfoTime").orElse("");
        }
        if (tag.contains("runInfoObjective")) {
            data.runInfoObjective = tag.getString("runInfoObjective").orElse("");
        }
        return data;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Objective obj : objectives) {
            list.add(obj.save(provider));
        }
        tag.put("objectives", list);
        tag.putBoolean("runInfoVictory", runInfoVictory);
        tag.putString("runInfoTime", runInfoTime);
        tag.putString("runInfoObjective", runInfoObjective);
        return tag;
    }
    
    // Add legacy save method just in case
    public CompoundTag save(CompoundTag tag) {
        return save(tag, null);
    }

    public static SpeedrunWorldData get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(new SavedDataType<SpeedrunWorldData>(
             DATA_NAME,
             SpeedrunWorldData::new,
             CODEC,
             null
        ));
    }

    public List<Objective> getObjectives() {
        return objectives;
    }

    public void setObjectives(List<Objective> objectives) {
        this.objectives.clear();
        this.objectives.addAll(objectives);
        setDirty();
    }

    public void setRunInfo(boolean isVictory, String time, String objectiveName) {
        this.runInfoVictory = isVictory;
        this.runInfoTime = time;
        this.runInfoObjective = objectiveName;
        setDirty();
    }
}
