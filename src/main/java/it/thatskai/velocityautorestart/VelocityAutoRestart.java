package it.thatskai.velocityautorestart;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "velocityautorestart",
        name = "VelocityAutoRestart",
        version = "1.0",
        authors = "ThatsKai",
        description = "A simple Velocity AutoRestart"
)
public class VelocityAutoRestart {

    @Getter
    @Inject
    private final Logger logger;

    @Getter
    private final ProxyServer proxyServer;

    @Getter
    private static VelocityAutoRestart instance;

    @Getter
    private YamlDocument config;

    private final Path directory;

    @Inject
    public VelocityAutoRestart(Logger logger, ProxyServer proxyServer, @DataDirectory Path directory) {
        this.logger = logger;
        this.proxyServer = proxyServer;
        this.directory = directory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();

        proxyServer.getCommandManager().register("autorestart", (SimpleCommand) invocation -> {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();
            if(!source.hasPermission("autorestart.admin")){
                source.sendMessage(translate("&cYou dont have permission to run this command."));
                return;
            }
            if(args.length >= 1 && args[0].equalsIgnoreCase("now")){
                source.sendMessage(translate("&cStai forzando il restart del server."));
                restart();
                return;
            }

            source.sendMessage(translate("&cRunning VelocityAutoRestart by @ThatsKai_"));
        });

        //LOAD AUTO RESTART
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        Runnable task = this::restart;

        Calendar now = Calendar.getInstance();
        Calendar scheduledTime = Calendar.getInstance();
        scheduledTime.set(Calendar.HOUR_OF_DAY, getConfig().getInt("auto-restart.hour"));
        scheduledTime.set(Calendar.MINUTE, getConfig().getInt("auto-restart.minutes"));
        scheduledTime.set(Calendar.SECOND, getConfig().getInt("auto-restart.seconds"));

        long delay = scheduledTime.getTimeInMillis() - now.getTimeInMillis();

        if (delay < 0) {
            delay += 24 * 60 * 60 * 1000;
        }
        executor.schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    public void restart(){
        logger.info("Server restart..");
        runScript();
        for(String s : config.getStringList("auto-restart.commands")){
            proxyServer.getCommandManager().executeAsync(proxyServer.getConsoleCommandSource(), s);
        }
    }

    public void runScript(){
        String scriptPath = config.getString("auto-restart.script");
        logger.info("Attemping to restart the server with the script.");
        try{
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec("cmd /c start " + scriptPath);
            } else {
                Runtime.getRuntime().exec(new String[] { "sh", scriptPath });
            }
        }catch (IOException ex){
            logger.error("An error occurred while attemping to restart the server with the script: "+ex.getMessage());
        }
    }

    public Component translate(String s){
        return Component.text(s.replace("&","§"));
    }

    public void loadConfig(){
        try{
            config = YamlDocument.create(new File(directory.toFile(), "config.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("configuration-version"))
                            .setOptionSorting(UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS).build());
            config.update();
            config.save();
        } catch (IOException ex){
            ex.printStackTrace();
            Optional<PluginContainer> plugin = proxyServer.getPluginManager().getPlugin("neohandler-velocity");
            plugin.ifPresent(pluginContainer -> pluginContainer.getExecutorService().shutdown());
        }
    }
}
