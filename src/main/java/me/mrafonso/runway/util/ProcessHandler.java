package me.mrafonso.runway.util;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.dialog.CommonDialogData;
import com.github.retrooper.packetevents.protocol.dialog.ConfirmationDialog;
import com.github.retrooper.packetevents.protocol.dialog.Dialog;
import com.github.retrooper.packetevents.protocol.dialog.DialogListDialog;
import com.github.retrooper.packetevents.protocol.dialog.MultiActionDialog;
import com.github.retrooper.packetevents.protocol.dialog.NoticeDialog;
import com.github.retrooper.packetevents.protocol.dialog.ServerLinksDialog;
import de.leonhard.storage.Config;
import io.github.miniplaceholders.api.MiniPlaceholders;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import me.mrafonso.runway.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.intellij.lang.annotations.Subst;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ProcessHandler {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final GsonComponentSerializer gsonSerializer = GsonComponentSerializer.gson();
    private final String noItalics = "<italic:false>";
    private final Config config;
    private final Config placeholders;
    private TagResolver placeholdersResolver;

    public ProcessHandler(ConfigManager configManager) {
        this.config = configManager.config();
        this.placeholders = configManager.placeholders();
    }

    public void reloadPlaceholders() {
        TagResolver.Builder builder = TagResolver.builder();

        for (String key : placeholders.singleLayerKeySet("custom-placeholders")) {
            @Subst("") String placeholder =  key.toLowerCase()
                                                .replace(" ", "_")
                                                .replace("-", "_");

            String value = placeholders.getString("custom-placeholders." + key);

            builder.resolver(Placeholder.parsed(placeholder, value));
        }
        placeholdersResolver = builder.build();
    }

    public Component processComponent(@Nullable Component component, @Nullable Player player) {
        return processComponent(component != null ? miniMessage.serialize(component) : null, player);
    }

    public Component processComponent(@Nullable String s, @Nullable Player player) {
        if (s == null) return Component.empty();

        boolean requirePrefixMM = config.getOrDefault("require-prefix.minimessage", true);
        boolean placeholderapi = config.getOrDefault("placeholder-hook.placeholderapi", false);
        boolean miniPlaceholders = config.getOrDefault("placeholder-hook.miniplaceholders", false);
        boolean requirePrefixP = config.getOrDefault("require-prefix.placeholders", true);
        boolean ignoreLegacy = config.getOrDefault("ignore-legacy", false);
        boolean disableItalics = config.getOrDefault("disable-italics", true);

        if (requirePrefixMM && !s.contains("[mm]")) {
            if (s.contains("§")) return Component.text(s);
            else return miniMessage.deserialize(s);
        }

        if (s.contains("§")) {
            if (ignoreLegacy) {
                s = s.replace("§", "&");
            } else {
                config.set("ignore-legacy", true);
                Bukkit.getLogger().log(Level.WARNING, "Detected Legacy colors! Runway is now ignoring legacy colors. \n" +
                    "To avoid receiving this message again, disable legacy colors in the config.");
            }
        }

        s = s.replace("[mm]", "");
        s = s.replace("\\<", "<");

        if (disableItalics) s = noItalics + s;

        if (player != null && (miniPlaceholders || placeholderapi)) {
            if (requirePrefixP && !s.contains("[p]")) {
                return miniMessage.deserialize(s, placeholdersResolver);
            }

            s = s.replace("[p]", "");
            if (placeholderapi) s = PlaceholderAPI.setPlaceholders(player, s);
            TagResolver resolver = placeholdersResolver;
            if (miniPlaceholders) {
                TagResolver playerResolver = MiniPlaceholders.getAudienceGlobalPlaceholders(player);
                resolver = TagResolver.builder().resolvers(playerResolver, placeholdersResolver).build();
            }
            return miniMessage.deserialize(s, resolver);
        }
        return miniMessage.deserialize(s, placeholdersResolver);
    }

    public List<Component> processComponent(@Nullable List<Component> components, @Nullable Player player) {
        if (components == null) return new ArrayList<>();

        List<Component> componentList = new ArrayList<>();
        for (Component c : components) {
            componentList.add(processComponent(c, player));
        }
        return componentList;
    }

    public ItemStack processItem(@Nullable ItemStack item, @Nullable Player player) {
        if (item == null) return null;

        org.bukkit.inventory.ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(item);
        ItemMeta meta = bukkitItem.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) meta.displayName(processComponent(meta.displayName(), player));
            if (meta.hasLore()) meta.lore(processComponent(meta.lore(), player));
            bukkitItem.setItemMeta(meta);
        }
        return SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
    }

    public List<ItemStack> processItems(@Nullable List<ItemStack> items, @Nullable Player player) {
        if (items == null) return new ArrayList<>();

        List<ItemStack> itemList = new ArrayList<>();
        for (ItemStack pItem : items) {
            itemList.add(processItem(pItem, player));
        }
        return itemList;
    }

    public Dialog processDialog(@Nullable Dialog dialog, @Nullable Player player) {
        if (dialog == null) return null;

        CommonDialogData common;
        if (dialog instanceof NoticeDialog d) common = d.getCommon();
        else if (dialog instanceof ConfirmationDialog d) common = d.getCommon();
        else if (dialog instanceof MultiActionDialog d) common = d.getCommon();
        else if (dialog instanceof DialogListDialog d) common = d.getCommon();
        else if (dialog instanceof ServerLinksDialog d) common = d.getCommon();
        else return dialog;

        net.kyori.adventure.text.Component newTitle = processComponent(common.getTitle(), player);
        net.kyori.adventure.text.Component newExternalTitle = common.getExternalTitle() != null
            ? processComponent(common.getExternalTitle(), player)
            : null;

        CommonDialogData newCommon = new CommonDialogData(
            newTitle,
            newExternalTitle,
            common.isCanCloseWithEscape(),
            common.isPause(),
            common.getAfterAction(),
            common.getBody(),
            common.getInputs()
        );

        if (dialog instanceof NoticeDialog d)
            return new NoticeDialog(newCommon, d.getAction());
        if (dialog instanceof ConfirmationDialog d)
            return new ConfirmationDialog(newCommon, d.getYesButton(), d.getNoButton());
        if (dialog instanceof MultiActionDialog d)
            return new MultiActionDialog(newCommon, d.getActions(), d.getExitAction(), d.getColumns());
        if (dialog instanceof DialogListDialog d)
            return new DialogListDialog(newCommon, d.getDialogs(), d.getExitAction(), d.getColumns(), d.getButtonWidth());
        if (dialog instanceof ServerLinksDialog d)
            return new ServerLinksDialog(newCommon, d.getExitAction(), d.getColumns(), d.getButtonWidth());

        return dialog;
    }
}
