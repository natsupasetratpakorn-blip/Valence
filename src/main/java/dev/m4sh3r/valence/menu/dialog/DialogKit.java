package dev.m4sh3r.valence.menu.dialog;

import dev.m4sh3r.valence.config.Messages;
import dev.m4sh3r.valence.util.Tasks;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Small helpers that keep every dialog in the plugin looking the same.
 */
final class DialogKit {

    static final int BUTTON_WIDTH = 150;
    static final int MENU_WIDTH = 100;
    static final int MEMBER_WIDTH = 80;
    static final int TEXT_WIDTH = 300;
    static final int ITEM_TEXT_WIDTH = 210;

    private static final ClickCallback.Options OPTIONS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(15))
            .build();

    // Audience#closeDialog arrived in Paper 1.21.8. On 1.21.7 every click closes the dialog before the next one opens.
    static final boolean CAN_CLOSE = hasCloseDialog();
    private static final DialogBase.DialogAfterAction AFTER_ACTION = CAN_CLOSE
            ? DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE
            : DialogBase.DialogAfterAction.CLOSE;

    private final Messages messages;
    private final Logger logger;

    DialogKit(Messages messages, Logger logger) {
        this.messages = messages;
        this.logger = logger;
    }

    ActionButton button(Component label, Component tooltip, Consumer<Player> action) {
        return button(label, tooltip, (player, view) -> action.accept(player));
    }

    ActionButton button(Component label, Component tooltip, BiConsumer<Player, DialogResponseView> action) {
        return button(label, tooltip, BUTTON_WIDTH, action);
    }

    ActionButton button(Component label, Component tooltip, int width, BiConsumer<Player, DialogResponseView> action) {
        ActionButton.Builder builder = ActionButton.builder(label)
                .width(width)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player player) {
                        // Menus are built off the main thread. Anything that needs it hops back on its own.
                        Tasks.async(() -> {
                            try {
                                action.accept(player, view);
                            } catch (RuntimeException e) {
                                logger.log(Level.WARNING, "A menu action failed for " + player.getName(), e);
                                close(player);
                            }
                        });
                    }
                }, OPTIONS));
        if (tooltip != null) {
            builder.tooltip(tooltip);
        }
        return builder.build();
    }

    ActionButton closeButton() {
        return button(messages.menu("close"), null, DialogKit::close);
    }

    /**
     * The client keeps its "waiting for server" screen open until a dialog arrives, and a plain close
     * does not clear that screen. Sending an empty dialog first and closing it right away does.
     */
    static void close(Player player) {
        if (CAN_CLOSE) {
            player.showDialog(EMPTY);
            player.closeDialog();
        }
    }

    private static final Dialog EMPTY = Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(Component.empty()).canCloseWithEscape(true).build())
            .type(DialogType.notice()));

    private static boolean hasCloseDialog() {
        try {
            Audience.class.getMethod("closeDialog");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    ActionButton back(Consumer<Player> to) {
        return button(messages.menu("back"), null, to);
    }

    DialogBody text(Component text) {
        return DialogBody.plainMessage(text, TEXT_WIDTH);
    }

    DialogBody item(ItemStack stack, Component description) {
        return DialogBody.item(stack)
                .description(DialogBody.plainMessage(description, ITEM_TEXT_WIDTH))
                .showDecorations(false)
                .showTooltip(false)
                .build();
    }

    DialogBody item(Material material, Component description) {
        return item(new ItemStack(material), description);
    }

    void show(Player player, Component title, List<DialogBody> body, List<DialogInput> inputs,
              List<ActionButton> buttons, ActionButton exit, int columns) {
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .afterAction(AFTER_ACTION)
                .body(body)
                .inputs(inputs)
                .build();
        // A page with nothing to click still keeps its Back button in the usual spot at the bottom.
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(base)
                .type(buttons.isEmpty() ? DialogType.notice(exit) : DialogType.multiAction(buttons, exit, columns)));
        player.showDialog(dialog);
    }

    void confirm(Player player, Component title, List<DialogBody> body, ActionButton yes, ActionButton no) {
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .afterAction(AFTER_ACTION)
                .body(body)
                .build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.confirmation(yes, no)));
        player.showDialog(dialog);
    }

    static List<SingleOptionDialogInput.OptionEntry> options(List<String> ids, List<Component> labels, String selected) {
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        boolean found = ids.contains(selected);
        for (int i = 0; i < ids.size(); i++) {
            boolean initial = found ? ids.get(i).equals(selected) : i == 0;
            entries.add(SingleOptionDialogInput.OptionEntry.create(ids.get(i), labels.get(i), initial));
        }
        return entries;
    }
}
