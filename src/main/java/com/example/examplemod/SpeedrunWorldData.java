package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.core.HolderLookup;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import java.util.Optional;
import net.minecraft.world.level.saveddata.SavedDataType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class SpeedrunWorldData extends SavedData {

    public static final Codec<SpeedrunWorldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Objective.CODEC.listOf().fieldOf("objectives").forGetter(SpeedrunWorldData::getObjectives),
        Codec.STRING.optionalFieldOf("gameMode", "COOPERATIVE").forGetter(d -> d.gameMode.name()),
        Codec.BOOL.optionalFieldOf("runFinished", false).forGetter(SpeedrunWorldData::isRunFinished),
        Codec.STRING.optionalFieldOf("winnerName", "").forGetter(SpeedrunWorldData::getWinnerName),
        Codec.STRING.optionalFieldOf("winnerUuid", "").forGetter(SpeedrunWorldData::getWinnerUuid),
        Codec.STRING.optionalFieldOf("finishTime", "").forGetter(SpeedrunWorldData::getFinishTime)
    ).apply(instance, (objectives, mode, finished, winnerName, winnerUuid, finishTime) -> {
        SpeedrunWorldData data = new SpeedrunWorldData();
        data.setObjectives(objectives);
        data.gameMode = SpeedrunGameMode.fromString(mode);
        data.runFinished = finished;
        data.winnerName = winnerName != null ? winnerName : "";
        data.winnerUuid = winnerUuid != null ? winnerUuid : "";
        data.finishTime = finishTime != null ? finishTime : "";
        return data;
    }));

    private static final String DATA_NAME = "speedrun_world_data";
    private final List<Objective> objectives = new ArrayList<>();
    private boolean runInfoVictory = false;
    private String runInfoTime = "";
    private String runInfoObjective = "";

    private SpeedrunGameMode gameMode = SpeedrunGameMode.COOPERATIVE;
    private boolean runFinished = false;
    private String winnerName = "";
    private String winnerUuid = "";
    private String finishTime = "";

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
                        SpeedrunRoulette.LOGGER.error("[WorldData] Failed to load objective at index {}", i, e);
                    }
                }
            }
        }
        SpeedrunRoulette.LOGGER.info("[WorldData] Loaded {} objectives from disk", data.objectives.size());
        if (tag.contains("runInfoVictory")) {
            data.runInfoVictory = tag.getBoolean("runInfoVictory").orElse(false);
        }
        if (tag.contains("runInfoTime")) {
            data.runInfoTime = tag.getString("runInfoTime").orElse("");
        }
        if (tag.contains("runInfoObjective")) {
            data.runInfoObjective = tag.getString("runInfoObjective").orElse("");
        }
        if (tag.contains("gameMode")) {
            data.gameMode = SpeedrunGameMode.fromString(tag.getString("gameMode").orElse("COOPERATIVE"));
        }
        if (tag.contains("runFinished")) {
            data.runFinished = tag.getBoolean("runFinished").orElse(false);
        }
        if (tag.contains("winnerName")) {
            data.winnerName = tag.getString("winnerName").orElse("");
        }
        if (tag.contains("winnerUuid")) {
            data.winnerUuid = tag.getString("winnerUuid").orElse("");
        }
        if (tag.contains("finishTime")) {
            data.finishTime = tag.getString("finishTime").orElse("");
        }
        return data;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CODEC.encodeStart(NbtOps.INSTANCE, this).result().ifPresent(encoded -> {
            if (encoded instanceof CompoundTag ct) {
                tag.merge(ct);
            }
        });
        return tag;
    }
    
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
        // New objectives start a fresh run result state
        this.runFinished = false;
        this.winnerName = "";
        this.winnerUuid = "";
        this.finishTime = "";
        setDirty();
    }

    public void setRunInfo(boolean isVictory, String time, String objectiveName) {
        this.runInfoVictory = isVictory;
        this.runInfoTime = time;
        this.runInfoObjective = objectiveName;
        setDirty();
    }

    public SpeedrunGameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(SpeedrunGameMode mode) {
        this.gameMode = mode != null ? mode : SpeedrunGameMode.COOPERATIVE;
        setDirty();
    }

    public boolean isRunFinished() {
        return runFinished;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public String getWinnerUuid() {
        return winnerUuid;
    }

    public String getFinishTime() {
        return finishTime;
    }

    /**
     * Attempts to mark the run finished with the given winner.
     * @return true if this call registered the first finish (or co-op shared finish), false if already finished.
     */
    public boolean tryFinishRun(String uuid, String name, String time) {
        if (runFinished) {
            return false;
        }
        runFinished = true;
        winnerUuid = uuid != null ? uuid : "";
        winnerName = name != null ? name : "";
        finishTime = time != null ? time : "";
        setDirty();
        return true;
    }

    public void clearRunResult() {
        runFinished = false;
        winnerName = "";
        winnerUuid = "";
        finishTime = "";
        setDirty();
    }

    public void updateForceCompleted(int index, boolean completed) {
        if (index >= 0 && index < objectives.size()) {
            objectives.get(index).setForceCompleted(completed);
            setDirty();
        }
    }
}
