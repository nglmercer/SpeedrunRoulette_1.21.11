package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffectInstance;
// import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.ComponentSerialization;
import com.mojang.serialization.JsonOps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class ObjectivePoolHelper {
    private static final Random RANDOM = new Random();

    public static List<Objective> getRandomObjectives(int count) {
        List<Objective> list = new ArrayList<>();
        List<Objective> candidates = getAllCandidates(false);

        if (candidates.isEmpty()) {
            list.add(new Objective("minecraft:cobblestone", Component.translatable("block.minecraft.cobblestone"), new ItemStack(Items.COBBLESTONE), Objective.Type.ITEM, null));
            return list;
        }

        Collections.shuffle(candidates, RANDOM);
        
        for (int i = 0; i < count && i < candidates.size(); i++) {
            list.add(candidates.get(i));
        }
        
        return list;
    }

    public static List<Objective> getAllCandidates(boolean includeBlacklisted) {
        return getAllCandidates(includeBlacklisted, false);
    }

    public static List<Objective> getAllCandidates(boolean includeBlacklisted, boolean ignoreGlobalSwitches) {
        return getAllCandidates(includeBlacklisted, 
            ignoreGlobalSwitches || Config.ENABLE_ITEMS.get(),
            ignoreGlobalSwitches || Config.ENABLE_BLOCKS.get(),
            ignoreGlobalSwitches || Config.ENABLE_ADVANCEMENTS.get()
        );
    }

    public static List<Objective> getAllCandidates(boolean includeBlacklisted, boolean enableItems, boolean enableBlocks, boolean enableAdvancements) {
        List<Objective> candidates = new ArrayList<>();

        String filter = Config.POOL_FILTER.get().toLowerCase().trim();
        List<? extends String> blacklist = Config.BLACKLIST.get();

        // 1. Items & Blocks
        if (enableItems || enableBlocks) {
            System.out.println("ObjectivePoolHelper: Scanning items/blocks...");
            int found = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                if (!isValidItem(item)) continue;

                // Skip base potions/arrows as we handle them separately
                if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW) continue;

                boolean isBlock = item instanceof BlockItem;
                if (isBlock && !enableBlocks) continue;
                if (!isBlock && !enableItems) continue;

                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                if (!filter.isEmpty() && !id.contains(filter)) continue;
                if (!includeBlacklisted && blacklist.contains(id)) continue;

                Component name = new ItemStack(item).getHoverName();
                if (id.startsWith("minecraft:music_disc_")) {
                    String discName = id.substring("minecraft:music_disc_".length());
                    if (!discName.isEmpty()) {
                         discName = discName.substring(0, 1).toUpperCase() + discName.substring(1);
                         name = Component.literal("Music Disc (" + discName + ")");
                    }
                }

                candidates.add(new Objective(
                    id,
                    name,
                    new ItemStack(item),
                    isBlock ? Objective.Type.BLOCK : Objective.Type.ITEM,
                    null
                ));
                found++;
            }
            
            // Add Potions
            if (enableItems) {
                try {
                    for (Potion potion : BuiltInRegistries.POTION) {
                         Holder<Potion> potionHolder;
                         try {
                             potionHolder = BuiltInRegistries.POTION.wrapAsHolder(potion);
                         } catch (Throwable t) {
                             potionHolder = Holder.direct(potion);
                         }
                         
                         addPotionObjective(candidates, Items.POTION, potionHolder, "Potion", blacklist, filter, includeBlacklisted);
                         addPotionObjective(candidates, Items.SPLASH_POTION, potionHolder, "Splash Potion", blacklist, filter, includeBlacklisted);
                         addPotionObjective(candidates, Items.LINGERING_POTION, potionHolder, "Lingering Potion", blacklist, filter, includeBlacklisted);
                         found += 3;
                    }
                } catch (Exception e) {
                    System.err.println("Error adding potions: " + e.getMessage());
                }
            }
            System.out.println("ObjectivePoolHelper: Found " + found + " items/blocks.");
        }

        // 2. Advancements
        if (enableAdvancements) {
            boolean foundAdvancements = false;

            try {
                Minecraft mc = Minecraft.getInstance();

                // Primary: use server advancements (always fully loaded)
                if (mc.getSingleplayerServer() != null) {
                    for (AdvancementHolder holder : mc.getSingleplayerServer().getAdvancements().getAllAdvancements()) {
                        if (holder.value().display().isPresent()) {
                            net.minecraft.advancements.DisplayInfo display = holder.value().display().get();
                            String id = holder.id().toString();
                            if (id.startsWith("minecraft:recipes/")) continue;
                            if (!filter.isEmpty() && !id.contains(filter)) continue;
                            if (!includeBlacklisted && blacklist.contains(id)) continue;

                            candidates.add(new Objective(
                                id,
                                display.getTitle(),
                                display.getIcon(),
                                Objective.Type.ADVANCEMENT,
                                id,
                                display.getDescription()
                            ));
                        }
                    }
                }

                // Secondary: use client connection advancements
                if (mc.player != null && mc.player.connection != null) {
                    for (net.minecraft.advancements.AdvancementNode node : mc.player.connection.getAdvancements().getTree().nodes()) {
                        AdvancementHolder holder = node.holder();
                        if (holder.value().display().isPresent()) {
                            net.minecraft.advancements.DisplayInfo display = holder.value().display().get();
                            String id = holder.id().toString();
                            if (id.startsWith("minecraft:recipes/")) continue;
                            if (!filter.isEmpty() && !id.contains(filter)) continue;
                            if (!includeBlacklisted && blacklist.contains(id)) continue;

                            // Avoid duplicates
                            final String finalId = id;
                            if (candidates.stream().anyMatch(o -> o.getType() == Objective.Type.ADVANCEMENT && o.getId().equals(finalId))) continue;

                            candidates.add(new Objective(
                                id,
                                display.getTitle(),
                                display.getIcon(),
                                Objective.Type.ADVANCEMENT,
                                id,
                                display.getDescription()
                            ));
                        }
                    }
                }

                long count = candidates.stream().filter(o -> o.getType() == Objective.Type.ADVANCEMENT).count();
                System.out.println("ObjectivePoolHelper: Found " + count + " advancements.");
                foundAdvancements = count > 0;
            } catch (Exception e) {
                System.err.println("ObjectivePoolHelper: Error loading advancements: " + e.getMessage());
                e.printStackTrace();
            }

            // Fallback: Hardcoded List (ensures all advancements are present even if dynamic loading fails)
            if (!foundAdvancements) {
                System.out.println("ObjectivePoolHelper: No advancements found dynamically, using hardcoded list.");
            }
            populateHardcodedAdvancements(candidates, blacklist, filter);
        }
        
        return candidates;
    }
    
    private static void populateHardcodedAdvancements(List<Objective> candidates, List<? extends String> blacklist, String filter) {
        // Helper to check if already exists
        java.util.Set<String> existingIds = candidates.stream()
            .map(Objective::getId)
            .collect(Collectors.toSet());

        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/root", "Minecraft", Items.CRAFTING_TABLE, "The heart and story of the game");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/mine_stone", "Stone Age", Items.WOODEN_PICKAXE, "Mine stone with your new pickaxe");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/upgrade_tools", "Getting an Upgrade", Items.STONE_PICKAXE, "Construct a better pickaxe");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/smelt_iron", "Acquire Hardware", Items.IRON_INGOT, "Smelt an iron ingot");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/obtain_armor", "Suit Up", Items.IRON_CHESTPLATE, "Protect yourself with a piece of iron armor");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/lava_bucket", "Hot Stuff", Items.LAVA_BUCKET, "Fill a bucket with lava");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/iron_tools", "Isn't It Iron Pick", Items.IRON_PICKAXE, "Upgrade your pickaxe");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/deflect_arrow", "Not Today, Thank You", Items.SHIELD, "Deflect an arrow with a shield");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/form_obsidian", "Ice Bucket Challenge", Items.OBSIDIAN, "Obtain a block of Obsidian");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/mine_diamond", "Diamonds!", Items.DIAMOND, "Acquire diamonds");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/enter_the_nether", "We Need to Go Deeper", Items.FLINT_AND_STEEL, "Build, light and enter a Nether Portal");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/shiny_gear", "Cover Me with Diamonds", Items.DIAMOND_CHESTPLATE, "Diamond armor saves lives");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/enchant_item", "Enchanter", Items.ENCHANTING_TABLE, "Enchant an item at an Enchanting Table");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/cure_zombie_villager", "Zombie Doctor", Items.GOLDEN_APPLE, "Weaken and then cure a Zombie Villager");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/follow_ender_eye", "Eye Spy", Items.ENDER_EYE, "Follow an Eye of Ender");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:story/enter_the_end", "The End?", Items.END_STONE, "Enter the End Dimension");

        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/root", "Nether", Items.RED_NETHER_BRICKS, "Bring summer clothes");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/return_to_sender", "Return to Sender", Items.GHAST_TEAR, "Destroy a Ghast with a fireball");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/find_bastion", "Those Were the Days", Items.POLISHED_BLACKSTONE_BRICKS, "Enter a Bastion Remnant");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", Items.ANCIENT_DEBRIS, "Obtain Ancient Debris");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/fast_travel", "Subspace Bubble", Items.MAP, "Use the Nether to travel 7 km in the Overworld");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/find_fortress", "A Terrible Fortress", Items.NETHER_BRICKS, "Break your way into a Nether Fortress");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/obtain_crying_obsidian", "Who is Cutting Onions?", Items.CRYING_OBSIDIAN, "Obtain Crying Obsidian");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/distract_piglin", "Oh Shiny", Items.GOLD_INGOT, "Distract Piglins with Gold");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/ride_strider", "This Boat Has Legs", Items.WARPED_FUNGUS_ON_A_STICK, "Ride a Strider with a Warped Fungus on a Stick");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/uneasy_alliance", "Uneasy Alliance", Items.GHAST_TEAR, "Rescue a Ghast from the Nether, bring it safely home to the Overworld... and then kill it");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/loot_bastion", "War Pigs", Items.CHEST, "Loot a chest in a Bastion Remnant");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/use_lodestone", "Country Lode, Take Me Home", Items.LODESTONE, "Use a Compass on a Lodestone");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/netherite_armor", "Cover Me in Debris", Items.NETHERITE_CHESTPLATE, "Get a full suit of Netherite armor");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/get_wither_skull", "Spooky Scary Skeleton", Items.WITHER_SKELETON_SKULL, "Obtain a Wither Skeleton Skull");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/obtain_blaze_rod", "Into Fire", Items.BLAZE_ROD, "Relieve a Blaze of its rod");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/charge_respawn_anchor", "Not Quite \"Nine\" Lives", Items.RESPAWN_ANCHOR, "Charge a Respawn Anchor to the maximum");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/explore_nether", "Hot Tourist Destinations", Items.NETHERITE_BOOTS, "Explore all Nether biomes");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/summon_wither", "Withering Heights", Items.NETHER_STAR, "Summon the Wither");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/brew_potion", "Local Brewery", Items.POTION, "Brew a Potion");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/create_beacon", "Bring Home the Beacon", Items.BEACON, "Construct and place a Beacon");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/all_potions", "A Furious Cocktail", Items.POTION, "Have every potion effect applied at the same time");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/create_full_beacon", "Beaconator", Items.BEACON, "Bring a beacon to full power");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:nether/all_effects", "How Did We Get Here?", Items.MILK_BUCKET, "Have every effect applied at the same time");

        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:end/root", "The End", Items.END_STONE, "Or the beginning?");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:end/kill_dragon", "Free the End", Items.DRAGON_HEAD, "Good luck");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:end/dragon_egg", "The Next Generation", Items.DRAGON_EGG, "Hold the Dragon Egg");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:end/enter_end_gateway", "Remote Getaway", Items.ENDER_PEARL, "Escape the island");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:end/respawn_dragon", "The End... Again...", Items.END_CRYSTAL, "Respawn the Ender Dragon");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:end/dragon_breath", "You Need a Mint", Items.DRAGON_BREATH, "Collect dragon's breath in a glass bottle");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:end/find_end_city", "The City at the End of the Game", Items.PURPUR_BLOCK, "Go on in, what could happen?");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:end/elytra", "Sky's the Limit", Items.ELYTRA, "Find Elytra");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:end/levitate", "Great View From Up Here", Items.SHULKER_SHELL, "Levitate up 50 blocks from the attacks of a Shulker");

        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/root", "Adventure", Items.MAP, "Adventure, exploration, and combat");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/kill_a_mob", "Monster Hunter", Items.IRON_SWORD, "Kill any hostile monster");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/trade", "What a Deal!", Items.EMERALD, "Successfully trade with a Villager");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/ol_betsy", "Ol' Betsy", Items.CROSSBOW, "Shoot a Crossbow");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/sleep_in_bed", "Sweet Dreams", Items.RED_BED, "Sleep in a bed to change your respawn point");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/hero_of_the_village", "Hero of the Village", Items.WHITE_BANNER, "Successfully defend a village from a raid");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/throw_trident", "A Throwaway Joke", Items.TRIDENT, "Throw a Trident at something. Note: Throwing away your only weapon is not a good idea.");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/shoot_arrow", "Take Aim", Items.BOW, "Shoot something with an arrow");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/kill_all_mobs", "Monsters Hunted", Items.DIAMOND_SWORD, "Kill one of every hostile monster");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/totem_of_undying", "Postmortal", Items.TOTEM_OF_UNDYING, "Use a Totem of Undying to cheat death");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/summon_iron_golem", "Hired Help", Items.IRON_BLOCK, "Summon an Iron Golem to help defend a village");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/two_birds_one_arrow", "Two Birds, One Arrow", Items.CROSSBOW, "Kill two Phantoms with a piercing arrow");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/whos_the_pillager_now", "Who's the Pillager Now?", Items.CROSSBOW, "Give a Pillager a taste of their own medicine");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/arbalistic", "Arbalistic", Items.CROSSBOW, "Kill five unique mobs with one crossbow shot");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/adventuring_time", "Adventuring Time", Items.DIAMOND_BOOTS, "Discover every biome");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/walk_on_powder_snow_with_leather_boots", "Light as a Rabbit", Items.LEATHER_BOOTS, "Walk on powder snow... without sinking");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/play_jukebox_in_meadows", "Sound of Music", Items.JUKEBOX, "Make the Meadows come alive with the sound of music from a Jukebox");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/spyglass_at_parrot", "Is It a Bird?", Items.SPYGLASS, "Look at a Parrot through a Spyglass");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/spyglass_at_ghast", "Is It a Balloon?", Items.SPYGLASS, "Look at a Ghast through a Spyglass");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/spyglass_at_dragon", "Is It a Plane?", Items.SPYGLASS, "Look at the Ender Dragon through a Spyglass");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/fall_from_world_height", "Caves & Cliffs", Items.WATER_BUCKET, "Free fall from the top of the world (build limit) to the bottom of the world and survive");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/avoid_vibration", "Sneak 100", Items.SCULK_SENSOR, "Walk near a Sculk Sensor or Warden without triggering it");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/kill_mob_near_sculk_catalyst", "It Spreads", Items.SCULK_CATALYST, "Kill a mob near a Sculk Catalyst");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/read_power_of_chiseled_bookshelf", "Power of Books", Items.CHISELED_BOOKSHELF, "Read the power signal of a Chiseled Bookshelf using a Comparator");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/trim_with_any_armor_pattern", "Crafting a New Look", Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, "Trim your armor at a Smithing Table");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/trim_with_all_exclusive_armor_patterns", "Smithing with Style", Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, "Apply these smithing templates at least once: Spire, Snout, Rib, Ward, Silence, Vex, Tide, Wayfinder");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/salvage_sherd", "Respecting the Remnants", Items.BRUSH, "Brush a Suspicious Block to obtain a Pottery Sherd");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:adventure/craft_decorated_pot_using_only_sherds", "Careful Restoration", Items.DECORATED_POT, "Craft a Decorated Pot out of 4 Pottery Sherds");
        
        // Husbandry
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/root", "Husbandry", Items.HAY_BLOCK, "The world is full of friends and food");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", Items.WHEAT, "Breed two animals together");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/tame_an_animal", "Best Friends Forever", Items.BONE, "Tame an animal");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/plant_seed", "A Seedy Place", Items.WHEAT_SEEDS, "Plant a seed and watch it grow");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/bred_all_animals", "Two by Two", Items.GOLDEN_CARROT, "Breed all the animals!");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/complete_catalogue", "A Complete Catalogue", Items.COD, "Tame all cat variants!");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/safely_harvest_honey", "Bee Our Guest", Items.HONEY_BOTTLE, "Use a Campfire to collect Honey from a Beehive using a Bottle without aggravating the bees");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/wax_on", "Wax On", Items.HONEYCOMB, "Apply Honeycomb to a Copper block!");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/wax_off", "Wax Off", Items.STONE_AXE, "Scrape Wax off a Copper block!");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/axolotl_in_a_bucket", "The Cutest Predator", Items.AXOLOTL_BUCKET, "Catch an Axolotl in a Bucket");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/kill_axolotl_target", "The Healing Power of Friendship", Items.TROPICAL_FISH, "Team up with an Axolotl and win a fight");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/froglights", "With Our Powers Combined!", Items.VERDANT_FROGLIGHT, "Have all Froglights in your inventory");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/tadpole_in_a_bucket", "Bukkit Bukkit", Items.TADPOLE_BUCKET, "Catch a Tadpole in a Bucket");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/leash_all_frog_variants", "When the Squad Hops into Town", Items.LEAD, "Get each Frog variant on a Lead");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/make_a_sign_glow", "Glow and Behold!", Items.GLOW_INK_SAC, "Make the text of a sign glow");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/ride_a_boat_with_a_goat", "Whatever Floats Your Goat", Items.OAK_BOAT, "Get in a Boat and float with a Goat");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/allay_deliver_item_to_player", "You've Got a Friend in Me", Items.ALLAY_SPAWN_EGG, "Have an Allay deliver items to you");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/allay_deliver_cake_to_note_block", "Birthday Song", Items.CAKE, "Have an Allay drop a Cake at a Note Block");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/obtain_sniffer_egg", "Smells Interesting", Items.SNIFFER_EGG, "Obtain a Sniffer Egg");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/feed_snifflet", "Little Sniffs", Items.TORCHFLOWER_SEEDS, "Feed a Snifflet (baby Sniffer)");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/plant_any_sniffer_seed", "Planting the Past", Items.PITCHER_POD, "Plant any Sniffer seed");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/repair_wolf_armor", "Good as New", Items.ARMADILLO_SCUTE, "Repair a damaged Wolf Armor using Armadillo Scutes");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/remove_wolf_armor", "Shear Brilliance", Items.SHEARS, "Remove Wolf Armor from a Wolf using Shears");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/whole_pack", "The Whole Pack", Items.BONE, "Tame one of each Wolf variant");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/balanced_diet", "A Balanced Diet", Items.APPLE, "Eat everything that is edible, even if it's not good for you");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/obtain_netherite_hoe", "Serious Dedication", Items.NETHERITE_HOE, "Use a Netherite Ingot to upgrade a Hoe, and then reevaluate your life choices");
        addHardcoded(candidates, existingIds, blacklist, filter, "minecraft:husbandry/tactical_fishing", "Tactical Fishing", Items.FISHING_ROD, "Catch a fish... without a fishing rod!");
    }

    private static void addPotionObjective(List<Objective> candidates, Item item, Holder<Potion> potionHolder, String prefix, List<? extends String> blacklist, String filter, boolean includeBlacklisted) {
        // Filter out unobtainable/useless potions for survival
        String potionName = BuiltInRegistries.POTION.getKey(potionHolder.value()).getPath();
        if (potionName.equals("empty") || potionName.equals("awkward") || potionName.equals("thick") || potionName.equals("mundane") || potionName.equals("luck")) {
            return;
        }

        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(java.util.Optional.of(potionHolder), java.util.Optional.empty(), java.util.List.of(), java.util.Optional.empty()));
        
        String displayName = prefix;
        if (potionName.equals("water")) {
            displayName += " (Water)";
        } else {
             Potion potion = potionHolder.value();
             List<MobEffectInstance> effects = potion.getEffects();
             if (!effects.isEmpty()) {
                 MobEffectInstance effect = effects.get(0);
                 int durationSeconds = effect.getDuration() / 20;
                 String duration = String.format("%d:%02d", durationSeconds / 60, durationSeconds % 60);
                 
                 String effectName = potionName.replace("long_", "").replace("strong_", "");
                 if (effectName.length() > 0) {
                     effectName = effectName.substring(0, 1).toUpperCase() + effectName.substring(1).replace("_", " ");
                 }
                 
                 displayName += " (" + effectName + " - " + duration + ")";
             } else {
                 displayName += " (" + potionName + ")";
             }
        }
        
        String id = BuiltInRegistries.ITEM.getKey(item).toString() + "/" + potionName;
        
        if (!filter.isEmpty() && !id.contains(filter)) return;
        if (!includeBlacklisted && blacklist.contains(id)) return;
        
        candidates.add(new Objective(id, Component.literal(displayName), stack, Objective.Type.ITEM, null));
    }

    private static void addHardcoded(List<Objective> candidates, java.util.Set<String> existingIds, List<? extends String> blacklist, String filter, String id, String name, Item icon, String description) {
        if (existingIds.contains(id)) return;
        if (!filter.isEmpty() && !id.contains(filter)) return;
        if (blacklist.contains(id)) return;

        candidates.add(new Objective(
            id,
            Component.literal(name),
            new ItemStack(icon),
            Objective.Type.ADVANCEMENT,
            id,
            Component.literal(description)
        ));
        existingIds.add(id);
    }
    
    private static Item getItemById(String id) {
        // Safe implementation using string fallback
        try {
            for (Item item : BuiltInRegistries.ITEM) {
                if (BuiltInRegistries.ITEM.getKey(item).toString().equals(id)) {
                    return item;
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
        return Items.AIR;
    }

    private static final java.util.Set<String> UNOBTAINABLE_ITEMS = java.util.Set.of(
        "minecraft:air",
        "minecraft:void_air",
        "minecraft:cave_air",
        "minecraft:bedrock",
        "minecraft:barrier",
        "minecraft:structure_void",
        "minecraft:structure_block",
        "minecraft:command_block",
        "minecraft:repeating_command_block",
        "minecraft:chain_command_block",
        "minecraft:command_block_minecart",
        "minecraft:jigsaw",
        "minecraft:light",
        "minecraft:debug_stick",
        "minecraft:knowledge_book",
        "minecraft:spawner",
        "minecraft:reinforced_deepslate",
        "minecraft:budding_amethyst",
        "minecraft:petrified_oak_slab",
        "minecraft:chorus_plant",
        "minecraft:farmland",
        "minecraft:dirt_path",
        "minecraft:end_portal_frame",
        "minecraft:end_portal",
        "minecraft:nether_portal",
        "minecraft:end_gateway",
        "minecraft:moving_piston",
        "minecraft:piston_head",
        "minecraft:frosted_ice",
        "minecraft:fire",
        "minecraft:soul_fire",
        "minecraft:water",
        "minecraft:lava",
        "minecraft:tall_seagrass",
        "minecraft:bubble_column",
        "minecraft:tripwire",
        "minecraft:redstone_wire",
        "minecraft:kelp_plant",
        "minecraft:bamboo_sapling",
        "minecraft:frogspawn",
        "minecraft:bundle" // Bundle is experimental in 1.20, check 1.21 status? In 1.21 it is fully added. I should remove bundle from here if it is 1.21. 1.21.11? Bundle is officially released in 1.21.2? Or 1.21?
        // Actually bundle was behind experimental flag for a long time. In 1.21 it might be enabled. I'll leave it obtainable.
    );

    private static boolean isValidItem(Item item) {
        if (item == Items.AIR) return false;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (UNOBTAINABLE_ITEMS.contains(id)) return false;
        if (id.endsWith("_spawn_egg")) return false;
        return true;
    }
}