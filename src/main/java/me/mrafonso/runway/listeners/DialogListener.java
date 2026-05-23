package me.mrafonso.runway.listeners;

import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.dialog.Dialog;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerShowDialog;
import me.mrafonso.runway.config.ConfigManager;
import me.mrafonso.runway.util.ProcessHandler;
import org.bukkit.entity.Player;

public class DialogListener extends AbstractListener {

    public DialogListener(ProcessHandler processHandler, ConfigManager configManager) {
        super(processHandler, configManager);
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent e) {
        if (!config.getOrDefault("listeners.dialogs", true) ||
            e.getPacketType() != PacketType.Play.Server.SHOW_DIALOG) return;

        WrapperPlayServerShowDialog packet = new WrapperPlayServerShowDialog(e);
        Player player = e.getPlayer();

        Dialog processed = handler.processDialog(packet.getDialog(), player);
        if (processed == packet.getDialog()) return;

        packet.setDialog(processed);
        e.markForReEncode(true);
    }
}
