/*
 * Copyright (C) 2017 andylizi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.andylizi.colormotd.bukkit;

import net.andylizi.colormotd.core.MotdService;
import net.andylizi.colormotd.core.SimpleMotdProvider;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class BukkitMain extends JavaPlugin {
    private static final Set<String> SUPPORTED_IMAGE_FORMAT = Arrays.stream(ImageIO.getReaderFileSuffixes())
            .map("."::concat)
            .collect(Collectors.toSet());
    private static Logger logger;

    public static Logger logger() {
        return logger;
    }

    ServicesManager serviceManager;
    BukkitMotdConfig motdConfig;
    BukkitMotdService motdService;

    @Override
    public void onLoad() {
        logger = getLogger();
    }

    @Override
    public void onEnable() {
        serviceManager = getServer().getServicesManager();
        motdConfig = loadConfig();
        motdService = new BukkitMotdService(new SimpleMotdProvider(motdConfig));
        serviceManager.register(MotdService.class, motdService, this, ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(new MotdListener(motdService), this); // todo configurable priority
    }

    public BukkitMotdConfig loadConfig() {
        saveDefaultConfig();
        reloadConfig();
        FileConfiguration config = getConfig();

        // check for ColorMOTD 1.x
        if (!config.contains("version")) {
            logger.info("ColorMOTD 1.x detected, renaming 'config.yml' to 'config.old.yml' ...");

            File oldLocation = new File(getDataFolder(), "config.yml");
            File newLocation = new File(getDataFolder(), "config.old.yml");
            newLocation.delete();
            if (!oldLocation.renameTo(newLocation)) {
                throw new RuntimeException("Unable to rename 'config.yml' to 'config.old.yml'!");
            }
            saveDefaultConfig();
            reloadConfig();
        }

        BukkitMotdConfig r = new BukkitMotdConfig();

        for (Object motdObj : config.getList("motds")) {
            if (motdObj instanceof String) {
                r.addMotd(((String) motdObj).replace("\\n", "\n"));
            } else if (motdObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) motdObj;
                String line1 = map.getOrDefault("line1", "").toString();
                String line2 = map.get("line2").toString();
                r.addMotd(line1 + (line2 == null ? "" : "\n" + line2));
            } else {
                throw new RuntimeException("Unknown motd type: " + motdObj.getClass().getCanonicalName());
            }
        }

        File iconFolder = new File(getDataFolder(), "favicons/");
        if(!iconFolder.isDirectory()) iconFolder.mkdirs();
        //noinspection ConstantConditions
        for(File file : iconFolder.listFiles((dir, name) ->
                SUPPORTED_IMAGE_FORMAT.stream().anyMatch(name::endsWith))) {
            try {
                BufferedImage image = ImageIO.read(file);
                r.addServerIcon(new BukkitMotdServerIcon(image));
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to load " + file.getName(), e);
            }
        }

        return r;
    }
}
